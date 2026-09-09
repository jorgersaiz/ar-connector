package com.vendor.arconnector.catalog;

/** Error en una fila concreta de un catálogo. */
public class CatalogRowException extends RuntimeException {

    private final int lineNumber;

    public CatalogRowException(int lineNumber, String message) {
        super("línea " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
