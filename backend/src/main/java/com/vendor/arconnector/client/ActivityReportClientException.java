package com.vendor.arconnector.client;

/** Fallo hablando con Activity Report (red, HTTP no 2xx, fichero local ausente...). */
public class ActivityReportClientException extends RuntimeException {

    public ActivityReportClientException(String message) {
        super(message);
    }

    public ActivityReportClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
