package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.FeedbackService;
import com.vendor.arconnector.service.UserRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Descarga del feedback de los envíos recientes. Los ficheros están
 * disponibles a partir de las 3:00; se lanza a las 4:30, después de los
 * catálogos, y se reintenta a media mañana por si BBVA fue tarde.
 */
@Component
@Order(2)
public class FeedbackSyncJob implements RecoverableJob {

    private static final Logger log = LoggerFactory.getLogger(FeedbackSyncJob.class);

    private final FeedbackService feedbackService;
    private final UserRegistrationService registrationService;
    private final JobRunner runner;
    private final String cron;

    public FeedbackSyncJob(FeedbackService feedbackService, UserRegistrationService registrationService, JobRunner runner,
                           @Value("${jobs.feedback-sync.cron:0 30 4,10 * * *}") String cron) {
        this.feedbackService = feedbackService;
        this.registrationService = registrationService;
        this.runner = runner;
        this.cron = cron;
    }

    @Scheduled(cron = "${jobs.feedback-sync.cron:0 30 4,10 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        runner.runIfDue(this, JobRunRegistry.Trigger.PROGRAMADO);
    }

    @Override
    public void execute() {
        log.info("Job de feedback: inicio");
        try {
            feedbackService.syncPending();
        } catch (RuntimeException e) {
            log.error("Job de feedback: error no controlado", e);
        }
        try {
            registrationService.syncPendingFeedback();
        } catch (RuntimeException e) {
            log.error("Job de feedback de registro de usuarios: error no controlado", e);
        }
    }

    @Override
    public String name() {
        return "feedback-sync";
    }

    @Override
    public String description() {
        return "Descarga del feedback de rechazos";
    }

    @Override
    public String cron() {
        return cron;
    }
}
