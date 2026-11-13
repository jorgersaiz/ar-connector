package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.ActivitiesUploadService;
import com.vendor.arconnector.service.UploadOutcome;
import com.vendor.arconnector.service.UserRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
 *
 * Si el equipo estaba apagado a la hora del envío, el comprobador de
 * ejecuciones perdidas lo lanza al encenderlo.
 */
@Component
@Order(4)
public class UploadJob implements RecoverableJob {

    private static final Logger log = LoggerFactory.getLogger(UploadJob.class);

    private final ActivitiesUploadService uploadService;
    private final UserRegistrationService registrationService;
    private final JobRunner runner;
    private final String cron;

    public UploadJob(ActivitiesUploadService uploadService, UserRegistrationService registrationService, JobRunner runner,
                     @Value("${jobs.upload.cron:0 0 13,18,21 * * *}") String cron) {
        this.uploadService = uploadService;
        this.registrationService = registrationService;
        this.runner = runner;
        this.cron = cron;
    }

    @Scheduled(cron = "${jobs.upload.cron:0 0 13,18,21 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        runner.runIfDue(this, JobRunRegistry.Trigger.PROGRAMADO);
    }

    @Override
    public void execute() {
        log.info("Job de envío de imputaciones: inicio");
        try {
            UploadOutcome outcome = uploadService.uploadNow();
            log.info("Job de envío de imputaciones: {} ({} días, {} filas, {} excluidos, {} huecos)",
                    outcome.status(), outcome.dayCount(), outcome.rowCount(), outcome.excludedDays(), outcome.gaps().size());
        } catch (RuntimeException e) {
            log.error("Job de envío de imputaciones: error no controlado", e);
        }
        try {
            UserRegistrationService.UploadResult r = registrationService.uploadNow();
            log.info("Job de registro de usuarios: {} ({} persona(s))", r.status(), r.count());
        } catch (RuntimeException e) {
            log.error("Job de registro de usuarios: error no controlado", e);
        }
    }

    @Override
    public String name() {
        return "upload";
    }

    @Override
    public String description() {
        return "Envío de imputaciones y altas a Activity Report";
    }

    @Override
    public String cron() {
        return cron;
    }
}
