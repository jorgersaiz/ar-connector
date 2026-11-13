package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DayOrigin;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.imports.CsvImportException;
import com.vendor.arconnector.imports.DayImportParser;
import com.vendor.arconnector.imports.ImportedDay;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importación de días desde CSV. Todo o nada: si alguna fila no se puede
 * leer, no existe la persona o el contenido no pasa las validaciones del
 * documento, no se guarda nada y se devuelven todos los problemas.
 */
@Service
public class DayImportService {

    private static final Logger log = LoggerFactory.getLogger(DayImportService.class);

    public record Summary(int daysCreated, int daysReplaced, int lines, int persons) {
    }

    private final PersonRepository personRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DayValidationService validationService;
    private final Clock clock;

    public DayImportService(PersonRepository personRepository, ActivityDayRepository activityDayRepository,
                            DayValidationService validationService, Clock clock) {
        this.personRepository = personRepository;
        this.activityDayRepository = activityDayRepository;
        this.validationService = validationService;
        this.clock = clock;
    }

    @Transactional
    public Summary importCsv(byte[] content) {
        List<ImportedDay> days;
        try {
            days = DayImportParser.parse(SemicolonCsv.parse(content));
        } catch (CatalogParseException e) {
            throw new CsvImportException(e.getErrors());
        }
        if (days.isEmpty()) {
            throw new CsvImportException(List.of("el fichero no contiene ningún día"));
        }

        Set<String> uids = days.stream().map(ImportedDay::uid).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Person> personByUid = personRepository.findByUidIn(uids).stream()
                .collect(Collectors.toMap(p -> p.getUid().toUpperCase(Locale.ROOT), Function.identity()));
        List<String> unknown = uids.stream().filter(u -> !personByUid.containsKey(u)).toList();
        if (!unknown.isEmpty()) {
            throw new CsvImportException(List.of("uid(s) desconocidos: " + String.join(", ", unknown)));
        }

        validationService.validate(days.stream().map(ImportedDay::toSubmission).toList());

        int created = 0;
        int replaced = 0;
        int lines = 0;
        Instant now = Instant.now(clock);
        for (ImportedDay imported : days) {
            Person person = personByUid.get(imported.uid());
            Optional<ActivityDay> existing = activityDayRepository.findByPersonAndActivityDate(person, imported.date());
            ActivityDay day = existing.orElseGet(() -> new ActivityDay(person, imported.date(), imported.kind(), DayOrigin.CSV_IMPORT));
            if (existing.isPresent()) {
                replaced++;
            } else {
                created++;
            }
            day.setKind(imported.kind());
            day.setOrigin(DayOrigin.CSV_IMPORT);
            day.setNote(imported.note());
            day.clearFeedback();
            day.clearLines();
            for (LineSubmission l : imported.lines()) {
                day.addLine(l.taskTypeCode(), l.sdaProjectId(), l.jiraIssueId(), l.nonSdaProject(), l.minutes());
                lines++;
            }
            day.setUpdatedAt(now);
            activityDayRepository.save(day);
        }

        log.info("Importación CSV: {} días nuevos, {} sustituidos, {} líneas, {} personas", created, replaced, lines, uids.size());
        return new Summary(created, replaced, lines, uids.size());
    }
}
