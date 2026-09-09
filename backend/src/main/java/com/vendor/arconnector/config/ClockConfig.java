package com.vendor.arconnector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj único de la aplicación en la zona horaria de Activity Report. Los
 * "días" de la cuota de llamadas y la fecha de envío se calculan con él, y
 * en los tests se sustituye por un reloj fijo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${jobs.timezone:Europe/Madrid}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
