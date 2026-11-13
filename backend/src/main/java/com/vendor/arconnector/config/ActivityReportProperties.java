package com.vendor.arconnector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

    public List<String> gpsCodeList() {
        return split(gpsCodes);
    }

    public List<String> geographyList() {
        return split(geographies);
    }

    /**
     * Entorno de Activity Report al que apunta {@code baseUrl}, para que el
     * panel lo muestre: DEV, AU, PRO, LOCAL (modo filesystem) u OTRO.
     */
    public String environment() {
        if (clientOrDefault() == ClientMode.FILESYSTEM) {
            return "LOCAL";
        }
        // BBVA ha cambiado los dominios (dev-bbva-gateway.appspot.com -> dev-gag.bbva.com):
        // el entorno se deduce del prefijo del host, no del dominio completo.
        String host = host(baseUrl);
        boolean bbva = host.endsWith(".bbva.com") || host.contains("bbva-gateway");
        if (!bbva) {
            return "OTRO";
        }
        if (host.startsWith("dev-")) {
            return "DEV";
        }
        if (host.startsWith("au-")) {
            return "AU";
        }
        return "PRO";
    }

    private static String host(String url) {
        if (isBlank(url)) {
            return "";
        }
        try {
            String h = java.net.URI.create(url.trim()).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static List<String> split(String csv) {
        if (isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
