package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Descarga del feedback de los envíos recientes. Los ficheros están
 * disponibles a partir de las 3:00; se lanza a las 4:30, después de los
 * catálogos, y se reintenta a media mañana por si BBVA fue tarde.
 */
@Component
public class FeedbackSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FeedbackSyncJob.class);

    private final FeedbackService feedbackService;

    public FeedbackSyncJob(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Scheduled(cron = "${jobs.feedback-sync.cron:0 30 4,10 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        log.info("Job de feedback: inicio");
        try {
            feedbackService.syncPending();
        } catch (RuntimeException e) {
            log.error("Job de feedback: error no controlado", e);
        }
    }
}
