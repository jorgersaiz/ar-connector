package com.vendor.arconnector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con la API de vendors de Activity Report y datos que el proveedor
 * comunica a sus administradores (entidad "Vendors" del documento).
 *
 * @param baseUrl             https://&lt;gateway&gt;/timereport/api/vendors
 * @param requestorEmail      email xxxxx.yyyyy.contractor@bbva.com que va en la cabecera Requestor
 * @param gpsCodes            códigos GPS de la empresa (separados por coma)
 * @param geographies         geografías del proveedor (separadas por coma)
 * @param client              HTTP contra BBVA, o FILESYSTEM para leer ficheros locales sin credenciales
 * @param localDir            directorio con &lt;catalogo&gt;.csv cuando client = FILESYSTEM
 * @param credentialsFile     ruta del JSON de la cuenta de servicio que entrega BBVA (nunca en el repositorio)
 * @param appId               valor de la cabecera X-BBVA-AppId; vacío = el client_id del JSON
 * @param delegateToRequestor true = el token se pide delegado al usuario Requestor
 * @param scope               ámbito del token; la guía de BBVA usa "email"
 * @param uploadFieldName     nombre del campo multipart con el fichero en las subidas
 */
@ConfigurationProperties(prefix = "activity-report")
public record ActivityReportProperties(
        String baseUrl,
        String requestorEmail,
        String gpsCodes,
        String geographies,
        ClientMode client,
        String localDir,
        String credentialsFile,
        String appId,
        boolean delegateToRequestor,
        String scope,
        String uploadFieldName
) {
    public static final String DEFAULT_SCOPE = "email";
    public static final String DEFAULT_UPLOAD_FIELD_NAME = "file";

    public enum ClientMode {
        HTTP,
        FILESYSTEM
    }

    public ClientMode clientOrDefault() {
        return client == null ? ClientMode.HTTP : client;
    }

    public String scopeOrDefault() {
        return isBlank(scope) ? DEFAULT_SCOPE : scope.trim();
    }

    public String uploadFieldNameOrDefault() {
        return isBlank(uploadFieldName) ? DEFAULT_UPLOAD_FIELD_NAME : uploadFieldName.trim();
    }

    public boolean hasCredentials() {
        return !isBlank(credentialsFile);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
