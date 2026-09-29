package com.vendor.arconnector.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cabeceras que la guía de consumo de APIs de BBVA exige a una aplicación externa. */
class ServiceAccountRequestAuthenticatorTest {

    private static final AccessTokenSource TOKENS = new AccessTokenSource() {
        @Override
        public String accessToken() {
            return "token-de-prueba";
        }

        @Override
        public String clientId() {
            return "1234567890";
        }
    };

    @Test
    void ponerLasTresCabeceras() {
        HttpHeaders headers = new HttpHeaders();
        new ServiceAccountRequestAuthenticator(TOKENS, " ana.lopez.contractor@bbva.com ", "arktic-app").apply(headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-de-prueba");
        assertThat(headers.getFirst("Requestor")).isEqualTo("ana.lopez.contractor@bbva.com");
        assertThat(headers.getFirst("X-BBVA-AppId")).isEqualTo("arktic-app");
    }

    @Test
    void sinAppIdConfiguradoSeEnviaElClientIdDeLaCredencial() {
        HttpHeaders headers = new HttpHeaders();
        new ServiceAccountRequestAuthenticator(TOKENS, "ana.lopez.contractor@bbva.com", " ").apply(headers);

        assertThat(headers.getFirst("X-BBVA-AppId")).isEqualTo("1234567890");
    }

    @Test
    void sinRequestorNoSeLlegaAEnviarNada() {
        assertThatThrownBy(() -> new ServiceAccountRequestAuthenticator(TOKENS, "", null).apply(new HttpHeaders()))
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("AR_REQUESTOR_EMAIL")
                .matches(e -> ((ActivityReportClientException) e).isNotSent());
    }

    @Test
    void sinCredencialConfiguradaNoSeLlegaAEnviarNada() {
        assertThatThrownBy(() -> new PendingRequestAuthenticator().apply(new HttpHeaders()))
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("AR_CREDENTIALS_FILE")
                .matches(e -> ((ActivityReportClientException) e).isNotSent());
    }
}
