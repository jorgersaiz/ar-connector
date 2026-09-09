package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP contra la API de vendors de Activity Report, con
 * {@link RestClient} (bloqueante; no hace falta WebFlux para unas pocas
 * llamadas al día).
 */
public class HttpActivityReportClient implements ActivityReportClient {

    private final RestClient restClient;
    private final RequestAuthenticator authenticator;

    public HttpActivityReportClient(String baseUrl, RequestAuthenticator authenticator) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.authenticator = authenticator;
    }

    @Override
    public byte[] downloadCatalogue(CatalogName catalogue) {
        try {
            byte[] body = restClient.get()
                    .uri("/catalogues/{catalogue}", catalogue.apiName())
                    .accept(MediaType.ALL)
                    .headers(authenticator::apply)
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                throw new ActivityReportClientException("Activity Report devolvió un cuerpo vacío para el catálogo " + catalogue.apiName());
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ActivityReportClientException("Activity Report respondió " + e.getStatusCode().value()
                    + " al descargar el catálogo " + catalogue.apiName() + ": " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ActivityReportClientException("No se ha podido conectar con Activity Report para descargar "
                    + catalogue.apiName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void uploadActivitiesFile(byte[] content, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            restClient.post()
                    .uri("/upload_activities_file")
                    .headers(authenticator::apply)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Un 400 trae un JSON con el estado de la cabecera y las líneas con nº de columnas incorrecto
            throw new ActivityReportClientException("Activity Report respondió " + e.getStatusCode().value()
                    + " al subir el fichero de imputaciones: " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ActivityReportClientException("No se ha podido conectar con Activity Report para subir el fichero de imputaciones: "
                    + e.getMessage(), e);
        }
    }
}
