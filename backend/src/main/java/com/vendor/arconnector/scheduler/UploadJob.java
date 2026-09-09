package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.ActivitiesUploadService;
import com.vendor.arconnector.service.UploadOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Envío programado del fichero de imputaciones.
 *
 * Por qué varias veces al día y todos los días:
 *  - BBVA procesa al día siguiente el ÚLTIMO fichero recibido, así que cada
 *    envío puede corregir al anterior sin coste.
 *  - Los cortes (día 19 y día 4) pueden caer en fin de semana, y ese día hay
 *    que enviar igualmente.
 *  - Tres ejecuciones agotan justo la cuota de 3 llamadas OK; si alguna
 *    falla, la cuota sube a 6 y hay margen para reintentar. Si el fichero no
 *    ha cambiado desde el último envío correcto, no se gasta cuota.
 */
@Component
public class UploadJob {

    private static final Logger log = LoggerFactory.getLogger(UploadJob.class);

    private final ActivitiesUploadService uploadService;

    public UploadJob(ActivitiesUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(cron = "${jobs.upload.cron:0 0 13,18,21 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        log.info("Job de envío de imputaciones: inicio");
        try {
            UploadOutcome outcome = uploadService.uploadNow();
            log.info("Job de envío de imputaciones: {} ({} días, {} filas, {} excluidos, {} huecos)",
                    outcome.status(), outcome.dayCount(), outcome.rowCount(), outcome.excludedDays(), outcome.gaps().size());
        } catch (RuntimeException e) {
            log.error("Job de envío de imputaciones: error no controlado", e);
        }
    }
}
