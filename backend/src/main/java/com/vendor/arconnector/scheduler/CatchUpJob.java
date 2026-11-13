package com.vendor.arconnector.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Comprobador de ejecuciones perdidas.
 *
 * Los procesos van a hora fija (catálogos 4:00, feedback 4:30 y 10:30, avisos
 * 9:00, envíos 13:00, 18:00 y 21:00). En un piloto en un PC, a esas horas el
 * equipo puede estar apagado o suspendido y Spring no las recupera. Este
 * comprobador se ejecuta un minuto después de arrancar y luego cada pocos
 * minutos, y lanza los procesos que se saltaron su última hora programada, en
 * el orden en que se habrían ejecutado.
 */
@Component
public class CatchUpJob {

    private static final Logger log = LoggerFactory.getLogger(CatchUpJob.class);

    private final List<RecoverableJob> jobs;
    private final JobRunner runner;
    private final boolean enabled;

    /** @param jobs en el orden de {@code @Order}: catálogos, feedback, avisos, envíos */
    public CatchUpJob(List<RecoverableJob> jobs, JobRunner runner, @Value("${jobs.catch-up.enabled:true}") boolean enabled) {
        this.jobs = jobs;
        this.runner = runner;
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "${jobs.catch-up.initial-delay:PT1M}", fixedDelayString = "${jobs.catch-up.interval:PT10M}")
    public void run() {
        if (!enabled) {
            return;
        }
        for (RecoverableJob job : jobs) {
            try {
                runner.runIfDue(job, JobRunRegistry.Trigger.RECUPERADO);
            } catch (RuntimeException e) {
                log.error("Comprobación de ejecuciones perdidas de '{}': error no controlado", job.name(), e);
            }
        }
    }
}
