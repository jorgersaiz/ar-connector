package com.vendor.arconnector.client;

import org.springframework.http.HttpHeaders;

/**
 * Autenticación contra el API Gateway de BBVA para una aplicación externa,
 * según su guía de consumo de APIs:
 *
 * <pre>
 *   Authorization: Bearer &lt;token de la cuenta de servicio&gt;
 *   Requestor:     email del usuario en cuyo nombre se llama
 *   X-BBVA-AppId:  client id que acompaña a la credencial
 * </pre>
 */
public class ServiceAccountRequestAuthenticator implements RequestAuthenticator {

    private final AccessTokenSource tokens;
    private final String requestorEmail;
    private final String appId;

    /**
     * @param appId valor de X-BBVA-AppId; vacío o null = el client_id de la credencial
     */
    public ServiceAccountRequestAuthenticator(AccessTokenSource tokens, String requestorEmail, String appId) {
        this.tokens = tokens;
        this.requestorEmail = requestorEmail == null ? "" : requestorEmail.trim();
        this.appId = appId == null ? "" : appId.trim();
    }

    @Override
    public void apply(HttpHeaders headers) {
        if (requestorEmail.isEmpty()) {
            throw ActivityReportClientException.notSent("Falta el email del Requestor (AR_REQUESTOR_EMAIL): "
                    + "Activity Report rechaza las peticiones que no lo llevan", null);
        }
        headers.setBearerAuth(tokens.accessToken());
        headers.set(REQUESTOR_HEADER, requestorEmail);
        headers.set(APP_ID_HEADER, appId.isEmpty() ? tokens.clientId() : appId);
    }
}
