package com.vendor.arconnector.client;

/** Fallo hablando con Activity Report (red, HTTP no 2xx, fichero local ausente...). */
public class ActivityReportClientException extends RuntimeException {

    private final Integer httpStatus;

    public ActivityReportClientException(String message) {
        this(message, null, null);
    }

    public ActivityReportClientException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ActivityReportClientException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** Código HTTP de la respuesta, o null si no llegó a haber respuesta. */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /**
     * true si Activity Report dice que el fichero pedido no existe (todavía).
     * No devuelve 404 sino 400 con
     * {@code {"code":400,"error_code":"INVALID_CONFIGURATION","message":"[API VENDORS] Feedback file not found."}}.
     */
    public boolean isFileNotFound() {
        if (httpStatus == null) {
            return false;
        }
        if (httpStatus == 404) {
            return true;
        }
        String message = getMessage() == null ? "" : getMessage().toLowerCase(java.util.Locale.ROOT);
        return httpStatus == 400 && message.contains("not found");
    }
}
