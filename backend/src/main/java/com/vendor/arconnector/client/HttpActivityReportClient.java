package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;

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
        return get("/catalogues/{catalogue}", "el catálogo " + catalogue.apiName(), catalogue.apiName());
    }

    @Override
    public byte[] downloadActivitiesFeedback(LocalDate submissionDate) {
        return get("/feedback_file/{date}", "el feedback del envío " + submissionDate, submissionDate.toString());
    }

    @Override
    public void uploadActivitiesFile(byte[] content, String fileName) {
        upload("/upload_activities_file", "el fichero de imputaciones", content, fileName);
    }

    @Override
    public void uploadUserRegistrationFile(byte[] content, String fileName) {
        upload("/upload_user_registration_file", "el fichero de registro de usuarios", content, fileName);
    }

    @Override
    public byte[] downloadUserRegistrationFeedback(LocalDate submissionDate) {
        return get("/user_registration_feedback_file/{date}", "el feedback del registro de usuarios del " + submissionDate, submissionDate.toString());
    }

    private void upload(String path, String what, byte[] content, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            restClient.post()
                    .uri(path)
                    .headers(authenticator::apply)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Un 400 trae un JSON con el estado de la cabecera y las líneas con nº de columnas incorrecto
            throw new ActivityReportClientException("Activity Report respondió " + e.getStatusCode().value()
                    + " al subir " + what + ": " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ActivityReportClientException("No se ha podido conectar con Activity Report para subir " + what + ": "
                    + e.getMessage(), e);
        }
    }

    private byte[] get(String uriTemplate, String what, Object... uriVars) {
        try {
            byte[] body = restClient.get()
                    .uri(uriTemplate, uriVars)
                    .accept(MediaType.ALL)
                    .headers(authenticator::apply)
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                throw new ActivityReportClientException("Activity Report devolvió un cuerpo vacío para " + what);
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ActivityReportClientException("Activity Report respondió " + e.getStatusCode().value()
                    + " al descargar " + what + ": " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ActivityReportClientException("No se ha podido conectar con Activity Report para descargar "
                    + what + ": " + e.getMessage(), e);
        }
    }
}
