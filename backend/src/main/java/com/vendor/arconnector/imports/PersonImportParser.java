package com.vendor.arconnector.imports;

import com.vendor.arconnector.catalog.CatalogRowException;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.domain.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CSV de alta masiva de personas. Separador ';', cabecera obligatoria,
 * columnas localizadas por nombre:
 *
 * <pre>
 *   uid;display_name;email;start_date;end_date;geography;gps_code;needs_registration
 *   XE12345;Ana López;ana.lopez@arktic.com;2026-11-01;;;;no
 *   XE67890;Bruno Pérez;bruno.perez@arktic.com;2026-11-15;;ES;0000417143;si
 * </pre>
 *
 *  - geography y gps_code vacíos: se usa el valor configurado para la empresa
 *    (AR_GEOGRAPHIES, AR_GPS_CODES) si es único.
 *  - needs_registration: si / no / vacío. "si" pide el alta en Activity Report
 *    (solo para quien nunca ha tenido acceso).
 *
 * Lógica pura, sin Spring.
 */
public final class PersonImportParser {

    public static final String HEADER = "uid;display_name;email;start_date;end_date;geography;gps_code;needs_registration";
    public static final List<String> REQUIRED_COLUMNS = List.of("uid", "display_name", "email", "start_date");

    /** Comprobación sencilla: algo@algo.algo, sin espacios. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s;]+@[^@\\s;]+\\.[^@\\s;]+$");
    /** Identificador de BBVA: letras y números (p. ej. XE123456). */
    private static final Pattern UID = Pattern.compile("^[A-Z0-9]{3,64}$");

    /** Valores por defecto de la empresa; null si no hay uno único configurado. */
    public record Defaults(String geography, String gpsCode) {
    }

    private PersonImportParser() {
    }

    public static List<ImportedPerson> parse(CsvTable table, Defaults defaults) {
        List<String> missing = table.missingColumns(REQUIRED_COLUMNS);
        if (!missing.isEmpty()) {
            throw new CsvImportException(List.of("faltan columnas en la cabecera: " + String.join(", ", missing)
                    + ". Esperada: " + HEADER));
        }

        List<ImportedPerson> persons = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> lineByUid = new HashMap<>();

        for (CsvRow row : table.rows()) {
            try {
                ImportedPerson p = parseRow(row, defaults);
                Integer previous = lineByUid.putIfAbsent(p.uid(), row.lineNumber());
                if (previous != null) {
                    throw new CatalogRowException(row.lineNumber(), "el uid " + p.uid() + " ya aparece en la línea " + previous);
                }
                persons.add(p);
            } catch (CatalogRowException e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
        if (persons.isEmpty()) {
            throw new CsvImportException(List.of("el fichero no contiene ninguna persona"));
        }
        return persons;
    }

    private static ImportedPerson parseRow(CsvRow row, Defaults defaults) {
        int line = row.lineNumber();
        String uid = Person.normalizeUid(row.requiredText("uid"));
        if (!UID.matcher(uid).matches()) {
            throw new CatalogRowException(line, "uid '" + uid + "' no válido: solo letras y números, como XE123456");
        }
        String name = row.requiredText("display_name");
        String email = row.requiredText("email");
        if (!EMAIL.matcher(email).matches()) {
            throw new CatalogRowException(line, "email '" + email + "' no válido");
        }
        LocalDate start = row.requiredUserDate("start_date");
        LocalDate end = row.optionalUserDate("end_date");
        if (end != null && end.isBefore(start)) {
            throw new CatalogRowException(line, "end_date (" + end + ") es anterior a start_date (" + start + ")");
        }

        String geography = upperOrNull(row.optionalText("geography"));
        if (geography == null && defaults.geography() == null) {
            throw new CatalogRowException(line, "geography vacía y no hay una geografía única configurada para la empresa (AR_GEOGRAPHIES)");
        }
        if (geography != null && geography.length() > 8) {
            throw new CatalogRowException(line, "geography '" + geography + "' demasiado larga (máximo 8 caracteres, p. ej. ES)");
        }
        String gps = row.optionalText("gps_code");
        if (gps == null && defaults.gpsCode() == null) {
            throw new CatalogRowException(line, "gps_code vacío y no hay un código GPS único configurado para la empresa (AR_GPS_CODES)");
        }
        if (gps != null && gps.length() > 32) {
            throw new CatalogRowException(line, "gps_code '" + gps + "' demasiado largo (máximo 32 caracteres)");
        }

        return new ImportedPerson(line, uid, truncate(name, 255), email, geography, gps, start, end,
                parseYesNo(row, line));
    }

    private static Boolean parseYesNo(CsvRow row, int line) {
        String v = row.text("needs_registration").toLowerCase(Locale.ROOT);
        return switch (v) {
            case "" -> null;
            case "si", "sí", "s", "yes", "y", "true", "1" -> true;
            case "no", "n", "false", "0" -> false;
            default -> throw new CatalogRowException(line, "needs_registration debe ser si, no o vacío: '" + v + "'");
        };
    }

    private static String upperOrNull(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
