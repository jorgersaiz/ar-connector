package com.vendor.arconnector.imports;

import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.catalog.CatalogRowException;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.domain.DayKind;

import java.time.LocalDate;
import java.util.*;

/**
 * CSV de importación de días para el manager (o el propio consultor).
 * Separador ';', cabecera obligatoria, columnas localizadas por nombre:
 *
 * <pre>
 *   uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note
 *   XE12345;2026-09-15;;01.01.01;SDATOOL-37040;;;480;
 *   XE12345;2026-09-15;;03.01;;;;60;Formación
 *   XE12345;2026-09-16;NON_WORKING;;;;;;Vacaciones
 * </pre>
 *
 *  - kind: WORKED (o vacío) / NON_WORKING. Un día NON_WORKING no lleva actividad ni minutos.
 *  - Varias filas con el mismo uid y fecha son varias líneas del mismo día.
 *  - Cada día importado SUSTITUYE por completo el que hubiera (BBVA también
 *    sobreescribe el día entero).
 *
 * Lógica pura, sin Spring.
 */
public final class DayImportParser {

    public static final List<String> REQUIRED_COLUMNS = List.of("uid", "date", "task_type_code", "minutes");

    private DayImportParser() {
    }

    public static List<ImportedDay> parse(CsvTable table) {
        List<String> missing = table.missingColumns(REQUIRED_COLUMNS);
        if (!missing.isEmpty()) {
            throw new CsvImportException(List.of("faltan columnas en la cabecera: " + String.join(", ", missing)
                    + ". Esperada: uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note"));
        }

        Map<String, ImportedDayBuilder> days = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (CsvRow row : table.rows()) {
            try {
                String uid = row.requiredText("uid").toUpperCase(Locale.ROOT);
                LocalDate date = row.requiredUserDate("date");
                DayKind kind = parseKind(row);
                String key = uid + "|" + date;

                ImportedDayBuilder day = days.computeIfAbsent(key, k -> new ImportedDayBuilder(uid, date, kind, row.optionalText("note")));
                if (day.kind != kind) {
                    throw new CatalogRowException(row.lineNumber(), "el día " + date + " de " + uid + " aparece como WORKED y como NON_WORKING");
                }
                if (day.note == null) {
                    day.note = row.optionalText("note");
                }

                if (kind == DayKind.NON_WORKING) {
                    if (!row.text("task_type_code").isEmpty() || !row.text("minutes").isEmpty()) {
                        throw new CatalogRowException(row.lineNumber(), "un día NON_WORKING no puede llevar actividad ni minutos");
                    }
                    continue;
                }

                String taskType = row.requiredText("task_type_code");
                int minutes = row.requiredInt("minutes");
                day.lines.add(new LineSubmission(taskType, row.optionalText("sda_project_id"), row.optionalText("jira_issue_id"),
                        row.optionalText("non_sda_project"), minutes));

            } catch (CatalogRowException e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
        return days.values().stream().map(ImportedDayBuilder::build).toList();
    }

    private static DayKind parseKind(CsvRow row) {
        String kind = row.text("kind").toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "", "WORKED" -> DayKind.WORKED;
            case "NON_WORKING" -> DayKind.NON_WORKING;
            default -> throw new CatalogRowException(row.lineNumber(), "kind debe ser WORKED, NON_WORKING o vacío: '" + kind + "'");
        };
    }

    private static final class ImportedDayBuilder {
        final String uid;
        final LocalDate date;
        final DayKind kind;
        String note;
        final List<LineSubmission> lines = new ArrayList<>();

        ImportedDayBuilder(String uid, LocalDate date, DayKind kind, String note) {
            this.uid = uid;
            this.date = date;
            this.kind = kind;
            this.note = note;
        }

        ImportedDay build() {
            return new ImportedDay(uid, date, kind, note, List.copyOf(lines));
        }
    }
}
