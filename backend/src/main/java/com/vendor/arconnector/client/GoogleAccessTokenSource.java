package com.vendor.arconnector.client;

import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * Token de la cuenta de servicio de Google que BBVA entrega al proveedor.
 *
 * Es el flujo que la guía de consumo de APIs de BBVA describe para las
 * "aplicaciones externas": con la clave privada del JSON se pide a Google un
 * token de acceso con el ámbito "email", opcionalmente delegado a un usuario.
 * La librería de Google guarda el token y lo renueva cuando caduca.
 *
 * La credencial se lee en la primera llamada, no al arrancar: sin ella el
 * panel tiene que seguir funcionando.
 */
public class GoogleAccessTokenSource implements AccessTokenSource {

    private final Path credentialsFile;
    private final String scope;
    private final String delegatedUser;
    private final HttpTransportFactory transportFactory;

    private volatile ServiceAccountCredentials credentials;

    /**
     * @param delegatedUser usuario al que se delega el token, o null para el token de la propia cuenta de servicio
     */
    public GoogleAccessTokenSource(Path credentialsFile, String scope, String delegatedUser) {
        this(credentialsFile, scope, delegatedUser, null);
    }

    GoogleAccessTokenSource(Path credentialsFile, String scope, String delegatedUser, HttpTransportFactory transportFactory) {
        this.credentialsFile = credentialsFile;
        this.scope = scope;
        this.delegatedUser = delegatedUser == null || delegatedUser.isBlank() ? null : delegatedUser.trim();
        this.transportFactory = transportFactory;
    }

    @Override
    public String accessToken() {
        ServiceAccountCredentials c = credentials();
        try {
            c.refreshIfExpired();
            return c.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw ActivityReportClientException.notSent("Google no ha concedido el token a la cuenta de servicio "
                    + c.getClientEmail() + (delegatedUser == null ? "" : " (delegado a " + delegatedUser + ")")
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String clientId() {
        return credentials().getClientId();
    }

    /** Cuenta de servicio de la credencial, para los mensajes de diagnóstico. */
    public String clientEmail() {
        return credentials().getClientEmail();
    }

    private ServiceAccountCredentials credentials() {
        ServiceAccountCredentials c = credentials;
        if (c == null) {
            synchronized (this) {
                c = credentials;
                if (c == null) {
                    c = load();
                    credentials = c;
                }
            }
        }
        return c;
    }

    private ServiceAccountCredentials load() {
        try (InputStream in = Files.newInputStream(credentialsFile)) {
            ServiceAccountCredentials loaded = transportFactory == null
                    ? ServiceAccountCredentials.fromStream(in)
                    : ServiceAccountCredentials.fromStream(in, transportFactory);
            ServiceAccountCredentials.Builder builder = loaded.toBuilder().setScopes(List.of(scope));
            if (delegatedUser != null) {
                builder.setServiceAccountUser(delegatedUser);
            }
            return builder.build();
        } catch (NoSuchFileException e) {
            throw ActivityReportClientException.notSent("No existe el fichero de la credencial de Activity Report: "
                    + credentialsFile.toAbsolutePath(), e);
        } catch (IOException | RuntimeException e) {
            // el mensaje de la librería no incluye la clave privada
            throw ActivityReportClientException.notSent("El fichero de la credencial de Activity Report ("
                    + credentialsFile.toAbsolutePath() + ") no es un JSON de cuenta de servicio válido: " + e.getMessage(), e);
        }
    }
}
