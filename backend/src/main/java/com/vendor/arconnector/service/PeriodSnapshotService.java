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
import java.util.List;

/**
 * Orquesta la generación del fichero de imputaciones del periodo abierto:
 * carga personas, días, plantillas, festivos y árbol de actividades desde la
 * base de datos, delega la lógica en {@link PeriodSnapshotBuilder} y
 * {@link ActivitiesFileBuilder}, y persiste los días materializados desde
 * plantilla para que queden visibles y editables.
 *
 * No envía nada a Activity Report: eso lo hará el cliente firmado cuando se
 * disponga de la documentación de autenticación de BBVA.
 */
@Service
public class PeriodSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PeriodSnapshotService.class);

    private final PersonRepository personRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DefaultTemplateRepository templateRepository;
    private final HolidayRepository holidayRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final ModifiablePeriodPolicy periodPolicy = new ModifiablePeriodPolicy();

    public PeriodSnapshotService(PersonRepository personRepository,
                                 ActivityDayRepository activityDayRepository,
                                 DefaultTemplateRepository templateRepository,
                                 HolidayRepository holidayRepository,
                                 TaskTypeRepository taskTypeRepository) {
        this.personRepository = personRepository;
        this.activityDayRepository = activityDayRepository;
        this.templateRepository = templateRepository;
        this.holidayRepository = holidayRepository;
        this.taskTypeRepository = taskTypeRepository;
    }

    public record Result(ActivitiesFile file, List<Gap> gaps, int generatedDays) {
    }

    /**
     * Genera el fichero para un envío hecho hoy. Los días creados desde
     * plantilla se guardan con origen TEMPLATE.
     */
    @Transactional
    public Result buildForToday() {
        return buildFor(LocalDate.now());
    }

    @Transactional
    public Result buildFor(LocalDate submissionDate) {
        List<LocalDate> dates = periodPolicy.submittableActivityDates(submissionDate);
        if (dates.isEmpty()) {
            return new Result(new ActivitiesFile(new byte[0], 0, 0), List.of(), 0);
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

        ActivitiesFile file = new ActivitiesFileBuilder(tree, periodPolicy).build(snapshot.days(), submissionDate);

        if (!snapshot.generatedDays().isEmpty()) {
            activityDayRepository.saveAll(snapshot.generatedDays());
        }

        log.info("Fichero de imputaciones generado para envío {}: {} días, {} filas, {} generados de plantilla, {} huecos",
                submissionDate, file.dayCount(), file.rowCount(), snapshot.generatedDays().size(), snapshot.gaps().size());

        return new Result(file, snapshot.gaps(), snapshot.generatedDays().size());
    }
}
