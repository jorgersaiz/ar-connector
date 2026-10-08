package com.vendor.arconnector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interruptor de los envíos a Activity Report (imputaciones y registro de
 * usuarios). Con {@code AR_UPLOADS_ENABLED=false} el conector sigue
 * descargando catálogos y feedback, pero no sube nada, ni desde el job
 * programado ni desde el botón "Enviar ahora". Pensado para las primeras
 * pruebas contra un entorno real, cuando aún no hay personas reales dadas de
 * alta y no se quiere que el job de las 13:00 mande datos de prueba.
 */
@Component
public class UploadGate {

    public static final String DISABLED_MESSAGE = "Los envíos a Activity Report están desactivados (AR_UPLOADS_ENABLED=false)";

    private final boolean enabled;

    public UploadGate(@Value("${activity-report.uploads-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
