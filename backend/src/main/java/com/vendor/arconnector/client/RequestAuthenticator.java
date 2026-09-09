package com.vendor.arconnector.client;

import org.springframework.http.HttpHeaders;

/** Añade a cada petición las cabeceras de autenticación que exige Activity Report. */
public interface RequestAuthenticator {

    /** Nombre de la cabecera con el email del solicitante, según el documento ("Requestor del Header"). */
    String REQUESTOR_HEADER = "Requestor";

    void apply(HttpHeaders headers);
}
