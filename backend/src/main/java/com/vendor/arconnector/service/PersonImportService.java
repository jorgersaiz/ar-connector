package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.config.ActivityReportProperties;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.imports.CsvImportException;
import com.vendor.arconnector.imports.ImportedPerson;
import com.vendor.arconnector.imports.PersonImportParser;
import com.vendor.arconnector.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Alta masiva de personas desde CSV. Todo o nada: si alguna fila falla no se
 * guarda ninguna.
 *
 * Una persona que ya existe (mismo uid) se actualiza con los datos del
 * fichero; las columnas opcionales vacías conservan lo que hubiera. Con
 * {@code dryRun} se devuelve lo que se haría sin guardar nada, para revisarlo
 * antes de importar.
 */
@Service
public class PersonImportService {

    private static final Logger log = LoggerFactory.getLogger(PersonImportService.class);

    public enum Action { CREAR, ACTUALIZAR, SIN_CAMBIOS }

    public record RowResult(int line, String uid, String displayName, Action action, List<String> changes) {
    }

    public record Summary(boolean dryRun, int created, int updated, int unchanged, int registrationsRequested,
                          List<RowResult> rows) {
    }

    private final PersonRepository personRepository;
    private final ActivityReportProperties props;
    private final Clock clock;

    public PersonImportService(PersonRepository personRepository, ActivityReportProperties props, Clock clock) {
        this.personRepository = personRepository;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Summary importCsv(byte[] content, boolean dryRun) {
        PersonImportParser.Defaults defaults = new PersonImportParser.Defaults(
                single(props.geographyList()), single(props.gpsCodeList()));
        List<ImportedPerson> imported;
        try {
            imported = PersonImportParser.parse(SemicolonCsv.parse(content), defaults);
        } catch (CatalogParseException e) {
            throw new CsvImportException(e.getErrors());
        }

        Map<String, Person> existing = personRepository.findByUidIn(imported.stream().map(ImportedPerson::uid).toList())
                .stream().collect(Collectors.toMap(Person::getUid, Function.identity()));

        List<RowResult> rows = new ArrayList<>();
        List<Person> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int registrations = 0;
        Instant now = Instant.now(clock);

        for (ImportedPerson in : imported) {
            Person p = existing.get(in.uid());
            if (p == null) {
                p = new Person(in.uid(), in.email(), in.displayName(), or(in.gpsCode(), defaults.gpsCode()),
                        or(in.geography(), defaults.geography()), in.startDate());
                p.setEndDate(in.endDate());
                List<String> notes = new ArrayList<>();
                if (Boolean.TRUE.equals(in.needsRegistration())) {
                    p.requestRegistration(true);
                    registrations++;
                    notes.add("se pedirá el alta en Activity Report");
                }
                toSave.add(p);
                created++;
                rows.add(new RowResult(in.lineNumber(), in.uid(), in.displayName(), Action.CREAR, notes));
                continue;
            }

            List<String> changes = applyChanges(p, in);
            boolean modified = !changes.isEmpty();
            if (in.needsRegistration() != null) {
                if (p.registrationLocked()) {
                    if (in.needsRegistration()) {
                        // aviso, no cambio: el alta ya está en marcha o hecha
                        changes.add("needs_registration ignorado: el alta ya se envió a Activity Report (" + p.getRegistrationStatus() + ")");
                    }
                } else if (p.requestRegistration(in.needsRegistration())) {
                    modified = true;
                    changes.add(in.needsRegistration() ? "se pedirá el alta en Activity Report" : "se retira la petición de alta");
                    if (in.needsRegistration()) {
                        registrations++;
                    }
                }
            }
            if (modified) {
                p.setUpdatedAt(now);
                toSave.add(p);
                updated++;
                rows.add(new RowResult(in.lineNumber(), in.uid(), in.displayName(), Action.ACTUALIZAR, changes));
            } else {
                unchanged++;
                rows.add(new RowResult(in.lineNumber(), in.uid(), in.displayName(), Action.SIN_CAMBIOS, changes));
            }
        }

        if (dryRun) {
            // Las personas existentes se han modificado en memoria para calcular los cambios, y Hibernate
            // las guardaría solas al cerrar la transacción aunque no se llame a save: hay que deshacerla.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        } else {
            personRepository.saveAll(toSave);
            log.info("Importación de personas: {} nuevas, {} actualizadas, {} sin cambios, {} altas en AR pedidas",
                    created, updated, unchanged, registrations);
        }
        return new Summary(dryRun, created, updated, unchanged, registrations, rows);
    }

    /** Aplica los datos del fichero a una persona existente y devuelve la lista de cambios. */
    private static List<String> applyChanges(Person p, ImportedPerson in) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(p.getDisplayName(), in.displayName())) {
            changes.add("nombre: " + p.getDisplayName() + " → " + in.displayName());
            p.setDisplayName(in.displayName());
        }
        if (!p.getEmail().equalsIgnoreCase(in.email())) {
            changes.add("email: " + p.getEmail() + " → " + in.email());
            p.setEmail(in.email());
        }
        if (!Objects.equals(p.getStartDate(), in.startDate())) {
            changes.add("alta: " + p.getStartDate() + " → " + in.startDate());
            p.setStartDate(in.startDate());
        }
        // vacío = se conserva: así reimportar el fichero no borra una baja hecha desde el panel
        if (in.endDate() != null && !in.endDate().equals(p.getEndDate())) {
            changes.add("baja: " + (p.getEndDate() == null ? "—" : p.getEndDate()) + " → " + in.endDate());
            p.setEndDate(in.endDate());
        }
        if (in.geography() != null && !in.geography().equals(p.getGeography())) {
            changes.add("geografía: " + p.getGeography() + " → " + in.geography());
            p.setGeography(in.geography());
        }
        if (in.gpsCode() != null && !in.gpsCode().equals(p.getGpsCode())) {
            changes.add("GPS: " + p.getGpsCode() + " → " + in.gpsCode());
            p.setGpsCode(in.gpsCode());
        }
        return changes;
    }

    private static String single(List<String> values) {
        return values.size() == 1 ? values.get(0) : null;
    }

    private static String or(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
