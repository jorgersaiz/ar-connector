package com.vendor.arconnector.imports;

import java.util.List;

/** Un CSV de importación (días o personas) no se ha podido importar; no se guarda nada. */
public class CsvImportException extends RuntimeException {

    private final List<String> errors;

    public CsvImportException(List<String> errors) {
        super(errors.size() + " error(es) en el CSV: " + String.join(" | ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
