package com.vendor.arconnector.client;

import com.vendor.arconnector.config.ActivityReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ActivityReportClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ActivityReportClientConfig.class);

    @Bean
    public RequestAuthenticator requestAuthenticator(ActivityReportProperties props) {
        return new PendingRequestAuthenticator(props);
    }

    @Bean
    public ActivityReportClient activityReportClient(ActivityReportProperties props, RequestAuthenticator authenticator) {
        if (props.clientOrDefault() == ActivityReportProperties.ClientMode.FILESYSTEM) {
            Path dir = Path.of(props.localDir() == null ? "samples/catalogues" : props.localDir());
            log.warn("Cliente de Activity Report en modo FILESYSTEM: los catálogos se leen de {}", dir.toAbsolutePath());
            return new FileSystemActivityReportClient(dir);
        }
        log.info("Cliente de Activity Report en modo HTTP contra {}", props.baseUrl());
        return new HttpActivityReportClient(props.baseUrl(), authenticator);
    }
}
