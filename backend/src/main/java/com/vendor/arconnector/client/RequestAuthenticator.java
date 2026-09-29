package com.vendor.arconnector.client;

import org.springframework.http.HttpHeaders;

/** Añade a cada petición las cabeceras de autenticación que exige el API Gateway de BBVA. */
public interface RequestAuthenticator {

    /** Email del usuario en cuyo nombre se hace la petición. */
    String REQUESTOR_HEADER = "Requestor";

    /** Identificador de la aplicación que llama (el client id que acompaña a la credencial). */
    String APP_ID_HEADER = "X-BBVA-AppId";

    void apply(HttpHeaders headers);
}
