package com.vendor.arconnector.imports;

import java.util.List;

/** El CSV de importación no se ha podido leer; no se guarda nada. */
public class DayImportException extends RuntimeException {

    private final List<String> errors;

    public DayImportException(List<String> errors) {
        super(errors.size() + " error(es) en el CSV: " + String.join(" | ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
