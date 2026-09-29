package com.vendor.arconnector.client;

import com.vendor.arconnector.config.ActivityReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class ActivityReportClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ActivityReportClientConfig.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    /** El catálogo de issues de Jira ocupa unos 30 MB. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    @Bean
    public RequestAuthenticator requestAuthenticator(ActivityReportProperties props) {
        if (props.clientOrDefault() == ActivityReportProperties.ClientMode.FILESYSTEM) {
            return new PendingRequestAuthenticator();   // no se usa: el cliente de ficheros no hace peticiones
        }
        if (!props.hasCredentials()) {
            log.warn("Modo HTTP sin credencial (AR_CREDENTIALS_FILE): las llamadas a Activity Report fallarán");
            return new PendingRequestAuthenticator();
        }
        Path file = Path.of(props.credentialsFile().trim());
        if (!Files.isReadable(file)) {
            log.error("No se puede leer la credencial de Activity Report en {}: las llamadas fallarán", file.toAbsolutePath());
        }
        String delegatedUser = props.delegateToRequestor() ? props.requestorEmail() : null;
        log.info("Autenticación con cuenta de servicio: credencial {}, ámbito {}, {}, X-BBVA-AppId {}",
                file.toAbsolutePath(), props.scopeOrDefault(),
                delegatedUser == null ? "sin delegación" : "delegada a " + delegatedUser,
                props.appId() == null || props.appId().isBlank() ? "= client_id de la credencial" : props.appId());
        return new ServiceAccountRequestAuthenticator(
                new GoogleAccessTokenSource(file, props.scopeOrDefault(), delegatedUser),
                props.requestorEmail(), props.appId());
    }

    @Bean
    public ActivityReportClient activityReportClient(ActivityReportProperties props, RequestAuthenticator authenticator) {
        if (props.clientOrDefault() == ActivityReportProperties.ClientMode.FILESYSTEM) {
            Path dir = Path.of(props.localDir() == null ? "samples/catalogues" : props.localDir());
            log.warn("Cliente de Activity Report en modo FILESYSTEM: los catálogos se leen de {}", dir.toAbsolutePath());
            return new FileSystemActivityReportClient(dir);
        }
        log.info("Cliente de Activity Report en modo HTTP contra {} (Requestor {})", props.baseUrl(), props.requestorEmail());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new HttpActivityReportClient(
                RestClient.builder().requestFactory(requestFactory).baseUrl(props.baseUrl()),
                authenticator, props.uploadFieldNameOrDefault());
    }
}
