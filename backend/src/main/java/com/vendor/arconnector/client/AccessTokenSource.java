package com.vendor.arconnector.client;

/** Proporciona el token OAuth vigente de la cuenta de servicio. */
public interface AccessTokenSource {

    /** Token de acceso válido; se renueva solo cuando caduca. */
    String accessToken();

    /** client_id de la credencial, que es el valor por defecto de la cabecera X-BBVA-AppId. */
    String clientId();
}
