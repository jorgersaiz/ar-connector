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
}
