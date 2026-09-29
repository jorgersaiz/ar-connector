package com.vendor.arconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El token se pide a Google con la clave de la cuenta de servicio. Aquí Google
 * está simulado y la clave se genera en el propio test: no hay credenciales
 * reales ni llamadas de red.
 */
class GoogleAccessTokenSourceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    private static String privateKeyPem;

    @TempDir
    Path dir;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    /** Google simulado: responde siempre con un token y guarda lo que se le pide. */
    private static final class FakeGoogle extends MockHttpTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        int status = 200;
        String response = "{\"access_token\":\"token-1\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest(url) {
                @Override
                public LowLevelHttpResponse execute() throws IOException {
                    urls.add(url);
                    bodies.add(getContentAsString());
                    return new MockLowLevelHttpResponse()
                            .setStatusCode(status)
                            .setContentType("application/json")
                            .setContent(response);
                }
            };
        }

        /** Contenido (claims) del JWT firmado con el que se pidió el último token. */
        JsonNode lastAssertionClaims() throws IOException {
            String body = bodies.get(bodies.size() - 1);
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals("assertion")) {
                    String jwt = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    return JSON.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
                }
            }
            throw new AssertionError("La petición de token no lleva assertion: " + body);
        }
    }

    private Path credentialsFile() throws IOException {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("type", "service_account");
        json.put("project_id", "proyecto-de-prueba");
        json.put("private_key_id", "clave-de-prueba");
        json.put("private_key", privateKeyPem);
        json.put("client_email", "arktic@proyecto-de-prueba.iam.gserviceaccount.com");
        json.put("client_id", "1234567890");
        json.put("token_uri", TOKEN_URI);
        Path file = dir.resolve("credencial.json");
        Files.writeString(file, JSON.writeValueAsString(json));
        return file;
    }

    @Test
    void pideElTokenConElAmbitoEmailYSinDelegar() throws Exception {
        FakeGoogle google = new FakeGoogle();
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(credentialsFile(), "email", null, () -> google);

        assertThat(source.accessToken()).isEqualTo("token-1");
        assertThat(source.clientId()).isEqualTo("1234567890");

        assertThat(google.urls).containsExactly(TOKEN_URI);
        JsonNode claims = google.lastAssertionClaims();
        assertThat(claims.get("iss").asText()).isEqualTo("arktic@proyecto-de-prueba.iam.gserviceaccount.com");
        assertThat(claims.get("scope").asText()).isEqualTo("email");
        assertThat(claims.has("sub")).isFalse();
    }

    @Test
    void conDelegacionElTokenSePideEnNombreDelRequestor() throws Exception {
        FakeGoogle google = new FakeGoogle();
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(credentialsFile(), "email",
                "ana.lopez.contractor@bbva.com", () -> google);

        source.accessToken();

        assertThat(google.lastAssertionClaims().get("sub").asText()).isEqualTo("ana.lopez.contractor@bbva.com");
    }

    @Test
    void elTokenSeReutilizaMientrasNoCaduca() throws Exception {
        FakeGoogle google = new FakeGoogle();
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(credentialsFile(), "email", null, () -> google);

        source.accessToken();
        source.accessToken();
        source.accessToken();

        assertThat(google.urls).hasSize(1);
    }

    @Test
    void siGoogleNoConcedeElTokenElErrorLoExplicaYNoCuentaComoLlamada() throws Exception {
        FakeGoogle google = new FakeGoogle();
        google.status = 401;
        google.response = "{\"error\":\"unauthorized_client\",\"error_description\":\"Client is unauthorized\"}";
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(credentialsFile(), "email",
                "ana.lopez.contractor@bbva.com", () -> google);

        assertThatThrownBy(source::accessToken)
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("arktic@proyecto-de-prueba.iam.gserviceaccount.com")
                .hasMessageContaining("delegado a ana.lopez.contractor@bbva.com")
                .hasMessageContaining("unauthorized_client")
                .matches(e -> ((ActivityReportClientException) e).isNotSent());
    }

    @Test
    void siElFicheroNoExisteElErrorDiceDondeSeBusco() {
        Path missing = dir.resolve("no-existe.json");
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(missing, "email", null, FakeGoogle::new);

        assertThatThrownBy(source::accessToken)
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("No existe")
                .hasMessageContaining("no-existe.json")
                .matches(e -> ((ActivityReportClientException) e).isNotSent());
    }

    @Test
    void unFicheroQueNoEsUnaCredencialSeRechazaSinMostrarSuContenido() throws Exception {
        Path file = dir.resolve("otro.json");
        Files.writeString(file, "{\"type\":\"service_account\",\"private_key\":\"contenido-secreto\"}");
        GoogleAccessTokenSource source = new GoogleAccessTokenSource(file, "email", null, FakeGoogle::new);

        assertThatThrownBy(source::accessToken)
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("no es un JSON de cuenta de servicio válido")
                .hasMessageNotContaining("contenido-secreto");
    }
}
