package com.vendor.arconnector.client;

import org.springframework.http.HttpHeaders;

/**
 * Se usa en modo HTTP cuando todavía no se ha configurado la credencial
 * (AR_CREDENTIALS_FILE). No llega a llamar a BBVA: una petición sin token
 * sería rechazada y gastaría cuota.
 */
public class PendingRequestAuthenticator implements RequestAuthenticator {

    @Override
    public void apply(HttpHeaders headers) {
        throw ActivityReportClientException.notSent("No hay credencial de Activity Report configurada: indica en "
                + "AR_CREDENTIALS_FILE la ruta del JSON de la cuenta de servicio, o usa AR_CLIENT=filesystem", null);
    }
}
