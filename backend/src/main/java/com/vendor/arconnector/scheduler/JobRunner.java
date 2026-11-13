package com.vendor.arconnector.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Ejecuta un proceso programado solo si le toca, y registra la ejecución.
 *
 * Lo usan tanto la programación normal (a su hora) como el comprobador de
 * ejecuciones perdidas. Al pasar los dos por aquí, un proceso nunca se
 * ejecuta dos veces para la misma hora programada.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Margen por si el planificador dispara unos milisegundos antes de la hora exacta. */
    private static final int TOLERANCE_SECONDS = 1;

    private final JobRunRegistry registry;
    private final Clock clock;

    public JobRunner(JobRunRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /** @return true si el proceso se ha ejecutado */
    public synchronized boolean runIfDue(RecoverableJob job, JobRunRegistry.Trigger trigger) {
        ZonedDateTime now = ZonedDateTime.now(clock).plusSeconds(TOLERANCE_SECONDS);
        Optional<ZonedDateTime> slot = MissedRuns.due(job.cron(),
                registry.find(job.name()).map(JobRunRegistry.JobRun::lastRunAt).orElse(null), now, job.catchUpOnlySameDay());
        if (slot.isEmpty()) {
            if (trigger == JobRunRegistry.Trigger.PROGRAMADO) {
                log.info("{}: ya se ejecutó para esta hora, no se repite", job.description());
            }
            return false;
        }
        if (trigger == JobRunRegistry.Trigger.RECUPERADO) {
            log.warn("{}: no se ejecutó a su hora ({}) porque el equipo estaba apagado o la aplicación parada; se ejecuta ahora",
                    job.description(), slot.get().format(WHEN));
        }
        // se registra antes de ejecutar: si el proceso falla, no se reintenta en bucle cada pocos minutos
        registry.record(job.name(), clock.instant(), trigger, slot.get().toInstant());
        job.execute();
        return true;
    }
}
