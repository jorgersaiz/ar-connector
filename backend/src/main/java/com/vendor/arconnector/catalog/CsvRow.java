package com.vendor.arconnector.catalog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Fila de un CSV con acceso tipado por nombre de columna (insensible a
 * mayúsculas). Guarda solo su array de valores; el índice de columnas es
 * compartido por todas las filas del fichero.
 */
public final class CsvRow {

    private final int lineNumber;
    private final Map<String, Integer> index;
    private final String[] values;

    CsvRow(int lineNumber, Map<String, Integer> indexByLowerCaseHeader, String[] values) {
        this.lineNumber = lineNumber;
        this.index = indexByLowerCaseHeader;
        this.values = values;
    }

    public int lineNumber() {
        return lineNumber;
    }

    /** Valor recortado; cadena vacía si la columna no existe o está vacía. */
    public String text(String column) {
        Integer i = index.get(column.toLowerCase(Locale.ROOT));
        if (i == null || i >= values.length) {
            return "";
        }
        return values[i].trim();
    }

    /** Valor recortado o null si está vacío. */
    public String optionalText(String column) {
        String v = text(column);
        return v.isEmpty() ? null : v;
    }

    public String requiredText(String column) {
        String v = text(column);
        if (v.isEmpty()) {
            throw error("la columna '" + column + "' es obligatoria y viene vacía");
        }
        return v;
    }

    /** Fecha YYYY-MM-DD o null si viene vacía. */
    public LocalDate optionalDate(String column) {
        String v = text(column);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw error("la columna '" + column + "' no es una fecha YYYY-MM-DD: '" + v + "'");
        }
    }

    public LocalDate requiredDate(String column) {
        LocalDate d = optionalDate(column);
        if (d == null) {
            throw error("la columna '" + column + "' es obligatoria y viene vacía");
        }
        return d;
    }

    /** true / false (insensible a mayúsculas); vacío se interpreta como false. */
    public boolean bool(String column) {
        String v = text(column).toLowerCase(Locale.ROOT);
        return switch (v) {
            case "", "false" -> false;
            case "true" -> true;
            default -> throw error("la columna '" + column + "' debe ser true/false: '" + v + "'");
        };
    }

    public int requiredInt(String column) {
        String v = requiredText(column);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw error("la columna '" + column + "' no es un entero: '" + v + "'");
        }
    }

    public Integer optionalInt(String column) {
        String v = text(column);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw error("la columna '" + column + "' no es un entero: '" + v + "'");
        }
    }

    private CatalogRowException error(String message) {
        return new CatalogRowException(lineNumber, message);
    }
}
