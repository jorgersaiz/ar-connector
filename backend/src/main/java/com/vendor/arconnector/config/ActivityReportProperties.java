package com.vendor.arconnector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con la API de vendors de Activity Report y datos que el proveedor
 * comunica a sus administradores (entidad "Vendors" del documento).
 *
 * @param baseUrl        https://&lt;domain&gt;/timereport/api/vendors
 * @param requestorEmail email xxxxx.yyyyy.contractor@bbva.com que va en la cabecera Requestor
 * @param gpsCodes       códigos GPS de la empresa (separados por coma)
 * @param geographies    geografías del proveedor (separadas por coma)
 * @param client         HTTP contra BBVA, o FILESYSTEM para leer ficheros locales sin credenciales
 * @param localDir       directorio con &lt;catalogo&gt;.csv cuando client = FILESYSTEM
 */
@ConfigurationProperties(prefix = "activity-report")
public record ActivityReportProperties(
        String baseUrl,
        String requestorEmail,
        String gpsCodes,
        String geographies,
        ClientMode client,
        String localDir
) {
    public enum ClientMode {
        HTTP,
        FILESYSTEM
    }

    public ClientMode clientOrDefault() {
        return client == null ? ClientMode.HTTP : client;
    }
}
