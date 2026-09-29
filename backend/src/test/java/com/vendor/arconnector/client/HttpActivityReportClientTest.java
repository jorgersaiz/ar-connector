package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Peticiones que salen hacia el API Gateway de BBVA (simulado, sin red). */
class HttpActivityReportClientTest {

    private static final String BASE = "https://au-bbva-gateway.appspot.com/timereport/api/vendors";

    private MockRestServiceServer bbva;
    private HttpActivityReportClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        bbva = MockRestServiceServer.bindTo(builder).build();
        RequestAuthenticator authenticator = headers -> {
            headers.setBearerAuth("token-1");
            headers.set(RequestAuthenticator.REQUESTOR_HEADER, "ana.lopez.contractor@bbva.com");
            headers.set(RequestAuthenticator.APP_ID_HEADER, "1234567890");
        };
        client = new HttpActivityReportClient(builder, authenticator, "fichero");
    }

    @Test
    void laDescargaDeUnCatalogoVaAutenticada() {
        bbva.expect(requestTo(BASE + "/catalogues/countries"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(header("Requestor", "ana.lopez.contractor@bbva.com"))
                .andExpect(header("X-BBVA-AppId", "1234567890"))
                .andRespond(withSuccess("sda_country;ended_margin\nES;1\n", MediaType.TEXT_PLAIN));

        byte[] body = client.downloadCatalogue(CatalogName.COUNTRIES);

        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("sda_country");
        bbva.verify();
    }

    @Test
    void laSubidaVaAutenticadaYConElNombreDeCampoConfigurado() {
        bbva.expect(requestTo(BASE + "/upload_activities_file"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(header("Requestor", "ana.lopez.contractor@bbva.com"))
                .andExpect(header("X-BBVA-AppId", "1234567890"))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(allOf(
                        containsString("name=\"fichero\""),
                        containsString("filename=\"activities_2026-09-29.csv\""),
                        containsString("date;person_id"))))
                .andRespond(withSuccess());

        client.uploadActivitiesFile("date;person_id\n".getBytes(StandardCharsets.UTF_8), "activities_2026-09-29.csv");

        bbva.verify();
    }

    @Test
    void elFeedbackQueTodaviaNoExisteSeDistingueDeUnError() {
        bbva.expect(requestTo(BASE + "/feedback_file/2026-09-28"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":400,\"error_code\":\"INVALID_CONFIGURATION\",\"message\":\"[API VENDORS] Feedback file not found.\"}"));

        assertThatThrownBy(() -> client.downloadActivitiesFeedback(LocalDate.parse("2026-09-28")))
                .isInstanceOf(ActivityReportClientException.class)
                .matches(e -> ((ActivityReportClientException) e).isFileNotFound())
                .matches(e -> !((ActivityReportClientException) e).isNotSent());
    }

    @Test
    void unRechazoDelGatewayConservaElCodigoYElMensaje() {
        bbva.expect(requestTo(BASE + "/catalogues/countries"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("application not authorized"));

        assertThatThrownBy(() -> client.downloadCatalogue(CatalogName.COUNTRIES))
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("application not authorized")
                .matches(e -> ((ActivityReportClientException) e).getHttpStatus() == 403);
    }

    @Test
    void siLaAutenticacionFallaLaPeticionNoSale() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer nadie = MockRestServiceServer.bindTo(builder).build();
        HttpActivityReportClient sinCredencial = new HttpActivityReportClient(builder, new PendingRequestAuthenticator(), "file");

        assertThatThrownBy(() -> sinCredencial.uploadActivitiesFile(new byte[] {1}, "f.csv"))
                .isInstanceOf(ActivityReportClientException.class)
                .matches(e -> ((ActivityReportClientException) e).isNotSent());
        nadie.verify();   // no se esperaba ninguna petición y no ha habido ninguna
    }
}
