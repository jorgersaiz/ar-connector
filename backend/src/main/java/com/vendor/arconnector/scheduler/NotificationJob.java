package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Avisos diarios a media mañana, después de catálogos (4:00) y feedback (4:30). */
@Component
@Order(3)
public class NotificationJob implements RecoverableJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

    private final NotificationService notificationService;
    private final JobRunner runner;
    private final String cron;

    public NotificationJob(NotificationService notificationService, JobRunner runner,
                           @Value("${jobs.notifications.cron:0 0 9 * * *}") String cron) {
        this.notificationService = notificationService;
        this.runner = runner;
        this.cron = cron;
    }

    @Scheduled(cron = "${jobs.notifications.cron:0 0 9 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        runner.runIfDue(this, JobRunRegistry.Trigger.PROGRAMADO);
    }

    @Override
    public void execute() {
        try {
            notificationService.runToday();
        } catch (RuntimeException e) {
            log.error("Job de avisos: error no controlado", e);
        }
    }

    @Override
    public String name() {
        return "notifications";
    }

    @Override
    public String description() {
        return "Avisos de huecos y rechazos";
    }

    @Override
    public String cron() {
        return cron;
    }

    /** Los avisos de ayer ya no tienen sentido: si se perdieron, se mandan los de hoy a su hora. */
    @Override
    public boolean catchUpOnlySameDay() {
        return true;
    }
}
