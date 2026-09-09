package com.vendor.arconnector.catalog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lector de los CSV que cede Activity Report: separador ';', UTF-8 (con o sin
 * BOM), finales de línea LF o CRLF, campos opcionalmente entrecomillados con
 * comillas dobles (y "" como escape), primera fila de cabecera.
 *
 * Las columnas se localizan por NOMBRE de cabecera, no por posición, para que
 * un cambio de orden o una columna nueva en BBVA no rompa la carga.
 */
public final class SemicolonCsv {

    public static final char SEPARATOR = ';';

    private SemicolonCsv() {
    }

    public static CsvTable parse(byte[] content) {
        return parse(new String(content, StandardCharsets.UTF_8));
    }

    public static CsvTable parse(String text) {
        List<List<String>> records = splitRecords(text);
        if (records.isEmpty()) {
            throw new CatalogParseException(List.of("fichero vacío: no hay fila de cabecera"));
        }

        List<String> headers = records.get(0).stream().map(String::trim).toList();
        if (headers.stream().allMatch(String::isBlank)) {
            throw new CatalogParseException(List.of("fichero vacío: no hay fila de cabecera"));
        }
        List<String> keys = headers.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();

        List<CsvRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> fields = records.get(i);
            int lineNumber = i + 1;
            if (fields.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (fields.size() > headers.size()) {
                errors.add("línea " + lineNumber + ": " + fields.size() + " columnas, la cabecera tiene " + headers.size());
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < keys.size(); c++) {
                values.put(keys.get(c), c < fields.size() ? fields.get(c) : "");
            }
            rows.add(new CsvRow(lineNumber, values));
        }
        if (!errors.isEmpty()) {
            throw new CatalogParseException(errors);
        }
        return new CsvTable(headers, rows);
    }

    private static List<List<String>> splitRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean recordHasContent = false;

        int i = 0;
        int n = text.length();
        if (n > 0 && text.charAt(0) == '﻿') {
            i = 1;
        }
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                } else {
                    field.append(c);
                }
                i++;
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    recordHasContent = true;
                }
                case SEPARATOR -> {
                    record.add(field.toString());
                    field.setLength(0);
                    recordHasContent = true;
                }
                case '\r' -> {
                    // se ignora; el salto real es el '\n' que le sigue
                }
                case '\n' -> {
                    record.add(field.toString());
                    field.setLength(0);
                    if (recordHasContent || !record.get(0).isBlank()) {
                        records.add(record);
                    }
                    record = new ArrayList<>();
                    recordHasContent = false;
                }
                default -> {
                    field.append(c);
                    recordHasContent = true;
                }
            }
            i++;
        }
        if (inQuotes) {
            throw new CatalogParseException(List.of("comillas sin cerrar al final del fichero"));
        }
        if (recordHasContent || field.length() > 0) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
