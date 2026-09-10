package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Avisos diarios a media mañana, después de catálogos (4:00) y feedback (4:30). */
@Component
public class NotificationJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

    private final NotificationService notificationService;

    public NotificationJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${jobs.notifications.cron:0 0 9 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        try {
            notificationService.runToday();
        } catch (RuntimeException e) {
            log.error("Job de avisos: error no controlado", e);
        }
    }
}
