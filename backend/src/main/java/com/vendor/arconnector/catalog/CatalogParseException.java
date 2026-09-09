package com.vendor.arconnector.catalog;

import java.util.List;

/**
 * El catálogo no se ha podido cargar. Se descarta ENTERO: un catálogo a medias
 * produciría validaciones equivocadas, así que se conserva el anterior.
 */
public class CatalogParseException extends RuntimeException {

    private final List<String> errors;

    public CatalogParseException(List<String> errors) {
        super(errors.size() + " error(es) leyendo el catálogo: " + String.join(" | ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
