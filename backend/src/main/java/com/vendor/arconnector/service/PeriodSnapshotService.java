package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.*;
import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DefaultTemplate;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Orquesta la generación del fichero de imputaciones del periodo abierto:
 * carga personas, días, plantillas, festivos y árbol de actividades desde la
 * base de datos, delega la lógica en {@link PeriodSnapshotBuilder} y
 * {@link ActivitiesFileBuilder}, y (opcionalmente) persiste los días
 * materializados desde plantilla para que queden visibles y editables.
 *
 * Los días con problemas de validación se excluyen del fichero y se devuelven
 * en {@link Result#problems()}: un día ausente no toca nada en BBVA, mientras
 * que bloquear el envío entero dejaría a todo el equipo sin imputar.
 *
 * No envía nada a Activity Report: eso lo hace {@link ActivitiesUploadService}.
 */
@Service
public class PeriodSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PeriodSnapshotService.class);

    private final PersonRepository personRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DefaultTemplateRepository templateRepository;
    private final HolidayRepository holidayRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final CatalogRulesService catalogRulesService;
    private final ModifiablePeriodPolicy periodPolicy = new ModifiablePeriodPolicy();

    public PeriodSnapshotService(PersonRepository personRepository,
                                 ActivityDayRepository activityDayRepository,
                                 DefaultTemplateRepository templateRepository,
                                 HolidayRepository holidayRepository,
                                 TaskTypeRepository taskTypeRepository,
                                 CatalogRulesService catalogRulesService) {
        this.personRepository = personRepository;
        this.activityDayRepository = activityDayRepository;
        this.templateRepository = templateRepository;
        this.holidayRepository = holidayRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.catalogRulesService = catalogRulesService;
    }

    /**
     * @param file           fichero listo para enviar (solo días válidos)
     * @param gaps           días laborables sin actividad ni plantilla
     * @param problems       días excluidos del fichero por no pasar las validaciones
     * @param generatedDays  días materializados desde plantilla o festivo local en esta ejecución
     * @param includedDayIds ids de los ActivityDay que van en el fichero (vacío si no se persistió)
     */
    public record Result(ActivitiesFile file,
                         List<Gap> gaps,
                         List<ValidationProblem> problems,
                         int generatedDays,
                         List<Long> includedDayIds) {

        public static Result empty() {
            return new Result(new ActivitiesFile(new byte[0], 0, 0), List.of(), List.of(), 0, List.of());
        }
    }

    /**
     * Genera el fichero para un envío hecho en {@code submissionDate}.
     *
     * @param persistGenerated true para guardar los días creados desde plantilla
     *                         (envío real); false para una previsualización
     */
    @Transactional
    public Result buildFor(LocalDate submissionDate, boolean persistGenerated) {
        List<LocalDate> dates = periodPolicy.submittableActivityDates(submissionDate);
        if (dates.isEmpty()) {
            return Result.empty();
        }
        LocalDate from = dates.get(0);
        LocalDate to = dates.get(dates.size() - 1);

        List<Person> persons = personRepository.findByActiveTrue();
        List<ActivityDay> existing = activityDayRepository.findInRangeWithLines(from, to);
        List<DefaultTemplate> templates = templateRepository.findAllOfActivePersonsWithLines();
        WorkCalendar calendar = new WorkCalendar(holidayRepository.findByDateBetween(from, to));
        TaskTree tree = TaskTree.fromCatalog(taskTypeRepository.findAll());

        if (tree.isEmpty()) {
            throw new IllegalStateException("El árbol de actividades está vacío: sincroniza el catálogo task_types antes de generar el fichero");
        }

        PeriodSnapshot snapshot = new PeriodSnapshotBuilder(periodPolicy, calendar)
                .build(submissionDate, persons, existing, templates);

        ActivitiesFileBuilder.LenientResult built = new ActivitiesFileBuilder(tree, periodPolicy, catalogRulesService.load())
                .buildSkippingInvalid(snapshot.days(), submissionDate);

        List<ActivityDay> persisted = new ArrayList<>(existing);
        if (persistGenerated && !snapshot.generatedDays().isEmpty()) {
            persisted.addAll(activityDayRepository.saveAll(snapshot.generatedDays()));
        }

        Set<String> excluded = built.excludedDayKeys();
        Map<String, Long> idByKey = new HashMap<>();
        for (ActivityDay d : persisted) {
            if (d.getId() != null) {
                idByKey.put(d.getPerson().getUid() + "|" + d.getActivityDate(), d.getId());
            }
        }
        List<Long> includedIds = snapshot.days().stream()
                .map(d -> d.uid() + "|" + d.date())
                .filter(k -> !excluded.contains(k))
                .map(idByKey::get)
                .filter(Objects::nonNull)
                .toList();

        if (!built.problems().isEmpty()) {
            log.warn("{} día(s) excluidos del fichero por validación:\n{}", built.excludedDays(),
                    built.problems().stream().map(ValidationProblem::toString).reduce((a, b) -> a + "\n" + b).orElse(""));
        }
        log.info("Fichero de imputaciones generado para envío {}: {} días, {} filas, {} generados de plantilla, {} excluidos, {} huecos",
                submissionDate, built.file().dayCount(), built.file().rowCount(), snapshot.generatedDays().size(),
                built.excludedDays(), snapshot.gaps().size());

        return new Result(built.file(), snapshot.gaps(), built.problems(), snapshot.generatedDays().size(), includedIds);
    }
}
