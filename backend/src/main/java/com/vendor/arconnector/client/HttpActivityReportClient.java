package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

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
                    + " al descargar el catálogo " + catalogue.apiName() + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new ActivityReportClientException("No se ha podido conectar con Activity Report para descargar "
                    + catalogue.apiName() + ": " + e.getMessage(), e);
        }
    }
}
