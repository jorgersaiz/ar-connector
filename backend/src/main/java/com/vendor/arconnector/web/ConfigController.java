package com.vendor.arconnector.web;

import com.vendor.arconnector.config.ActivityReportProperties;
import com.vendor.arconnector.config.UploadGate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Configuración del conector que el panel necesita conocer: contra qué
 * entorno de Activity Report habla y los datos de la empresa registrados en
 * BBVA (para rellenar por defecto el alta de personas). Nada secreto.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /**
     * @param environment DEV, AU, PRO, LOCAL (modo filesystem) u OTRO
     * @param gpsCodes    códigos GPS de la empresa comunicados a BBVA
     * @param geographies geografías comunicadas a BBVA
     */
    public record ConfigDto(String environment, String baseUrl, String requestorEmail, boolean credentialsConfigured,
                            boolean uploadsEnabled, List<String> gpsCodes, List<String> geographies) {
    }

    private final ActivityReportProperties props;
    private final UploadGate uploadGate;

    public ConfigController(ActivityReportProperties props, UploadGate uploadGate) {
        this.props = props;
        this.uploadGate = uploadGate;
    }

    @GetMapping
    public ConfigDto get() {
        return new ConfigDto(props.environment(), props.baseUrl(), props.requestorEmail(),
                props.hasCredentials(), uploadGate.isEnabled(), props.gpsCodeList(), props.geographyList());
    }
}
