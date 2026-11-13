package com.vendor.arconnector.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lector de los CSV que cede Activity Report: separador ';', UTF-8 (con o sin
 * BOM), finales de línea LF o CRLF, primera fila de cabecera.
 *
 * Las columnas se localizan por NOMBRE de cabecera, no por posición, para que
 * un cambio de orden o una columna nueva en BBVA no rompa la carga.
 *
 * Comillas: solo abren un campo entrecomillado si son el PRIMER carácter del
 * campo (CSV estándar, con "" como escape). Unas comillas a mitad de un texto
 * libre ("Pantalla 24"") son un carácter más. Los ficheros reales de BBVA no
 * traen comillas ni ';' dentro de los campos.
 *
 * Tamaño: el catálogo de issues de Jira real tiene ~270.000 filas y 29 MB, así
 * que cada fila guarda solo su array de valores y comparte el índice de columnas.
 *
 * Si el contenido llega comprimido (zip o gzip) se descomprime de forma
 * transparente.
 */
public final class SemicolonCsv {

    public static final char SEPARATOR = ';';

    private SemicolonCsv() {
    }

    public static CsvTable parse(byte[] content) {
        return parse(decodeText(decompressIfNeeded(content)));
    }

    /**
     * UTF-8 si el contenido es UTF-8 válido; si no, Windows-1252. Los ficheros
     * de BBVA son UTF-8, pero un CSV guardado con el Excel en español ("CSV
     * delimitado por punto y coma") usa Windows-1252, y con UTF-8 una tilde
     * ("José") se convertiría en un carácter ilegible.
     */
    static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    public static CsvTable parse(String text) {
        List<Rec> records = splitRecords(text);
        if (records.isEmpty() || allBlank(records.get(0).fields)) {
            throw new CatalogParseException(List.of("fichero vacío: no hay fila de cabecera"));
        }

        String[] rawHeaders = records.get(0).fields;
        List<String> headers = new ArrayList<>(rawHeaders.length);
        Map<String, Integer> index = new HashMap<>();
        for (int c = 0; c < rawHeaders.length; c++) {
            String h = rawHeaders[c].trim();
            headers.add(h);
            index.putIfAbsent(h.toLowerCase(Locale.ROOT), c);
        }

        List<CsvRow> rows = new ArrayList<>(records.size());
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            Rec rec = records.get(i);
            if (allBlank(rec.fields)) {
                continue;
            }
            if (rec.fields.length > headers.size()) {
                if (errors.size() < 20) {
                    errors.add("línea " + rec.line + ": " + rec.fields.length + " columnas, la cabecera tiene " + headers.size());
                }
                continue;
            }
            rows.add(new CsvRow(rec.line, index, rec.fields));
        }
        if (!errors.isEmpty()) {
            throw new CatalogParseException(errors);
        }
        return new CsvTable(List.copyOf(headers), rows);
    }

    /** Descomprime zip (primera entrada) o gzip; si no está comprimido devuelve el contenido tal cual. */
    static byte[] decompressIfNeeded(byte[] content) {
        if (content == null || content.length < 4) {
            return content == null ? new byte[0] : content;
        }
        try {
            if (content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4) {
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                    ZipEntry entry = zip.getNextEntry();
                    while (entry != null && entry.isDirectory()) {
                        entry = zip.getNextEntry();
                    }
                    if (entry == null) {
                        throw new CatalogParseException(List.of("el zip recibido no contiene ningún fichero"));
                    }
                    return zip.readAllBytes();
                }
            }
            if ((content[0] & 0xff) == 0x1f && (content[1] & 0xff) == 0x8b) {
                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
                    return gzip.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new CatalogParseException(List.of("no se ha podido descomprimir el fichero: " + e.getMessage()));
        }
        return content;
    }

    private record Rec(int line, String[] fields) {
    }

    private static boolean allBlank(String[] fields) {
        for (String f : fields) {
            if (!f.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static List<Rec> splitRecords(String text) {
        List<Rec> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStart = true;
        int line = 1;
        int recordLine = 1;

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
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
                i++;
                continue;
            }
            if (c == '"' && fieldStart) {
                inQuotes = true;
                fieldStart = false;
            } else if (c == SEPARATOR) {
                record.add(field.toString());
                field.setLength(0);
                fieldStart = true;
            } else if (c == '\n') {
                record.add(field.toString());
                field.setLength(0);
                records.add(new Rec(recordLine, record.toArray(String[]::new)));
                record = new ArrayList<>();
                fieldStart = true;
                line++;
                recordLine = line;
            } else if (c != '\r') {
                field.append(c);
                fieldStart = false;
            }
            i++;
        }
        if (inQuotes) {
            throw new CatalogParseException(List.of("comillas sin cerrar en el campo que empieza en la línea " + recordLine));
        }
        if (!record.isEmpty() || field.length() > 0) {
            record.add(field.toString());
            records.add(new Rec(recordLine, record.toArray(String[]::new)));
        }
        return records;
    }
}
