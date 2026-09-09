package com.vendor.arconnector.client;

import com.vendor.arconnector.config.ActivityReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autenticación PROVISIONAL: solo informa la cabecera Requestor.
 *
 * La firma con la Service Account (credencial JSON que entrega BBVA, esquema
 * de firma descrito en su documentación de consumo de APIs de GCP) se
 * implementará cuando se disponga de esa documentación y de la credencial.
 * Hasta entonces las llamadas HTTP reales fallarán con 401/403.
 */
public class PendingRequestAuthenticator implements RequestAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(PendingRequestAuthenticator.class);

    private final ActivityReportProperties props;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public PendingRequestAuthenticator(ActivityReportProperties props) {
        this.props = props;
    }

    @Override
    public void apply(HttpHeaders headers) {
        if (props.requestorEmail() != null && !props.requestorEmail().isBlank()) {
            headers.set(REQUESTOR_HEADER, props.requestorEmail());
        }
        if (warned.compareAndSet(false, true)) {
            log.warn("La firma de peticiones con la Service Account de Activity Report NO está implementada; "
                    + "solo se envía la cabecera Requestor. Pendiente de la documentación de BBVA.");
        }
    }
}
