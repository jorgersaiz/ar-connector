package com.vendor.arconnector.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Fichero CSV ya leído: cabeceras (tal como venían) y filas accesibles por nombre de columna. */
public record CsvTable(List<String> headers, List<CsvRow> rows) {

    public boolean hasColumn(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return headers.stream().anyMatch(h -> h.toLowerCase(Locale.ROOT).equals(wanted));
    }

    public List<String> missingColumns(Collection<String> required) {
        return required.stream().filter(c -> !hasColumn(c)).toList();
    }

    /** Lanza {@link CatalogParseException} si falta alguna de las columnas indicadas. */
    public void requireColumns(String... required) {
        List<String> missing = missingColumns(List.of(required));
        if (!missing.isEmpty()) {
            throw new CatalogParseException(List.of(
                    "faltan columnas en la cabecera: " + String.join(", ", missing)
                            + " (cabecera recibida: " + String.join(";", headers) + ")"));
        }
    }
}
