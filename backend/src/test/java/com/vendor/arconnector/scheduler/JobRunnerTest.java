package com.vendor.arconnector.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JobRunnerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Registro en memoria, en lugar de la tabla job_run. */
    private static final class MemoryRegistry extends JobRunRegistry {
        final Map<String, JobRun> runs = new HashMap<>();

        MemoryRegistry() {
            super(null);
        }

        @Override
        public Optional<JobRun> find(String jobName) {
            return Optional.ofNullable(runs.get(jobName));
        }

        @Override
        public List<JobRun> findAll() {
            return List.copyOf(runs.values());
        }

        @Override
        public void record(String jobName, Instant runAt, Trigger trigger, Instant scheduledFor) {
            runs.put(jobName, new JobRun(jobName, runAt, trigger, scheduledFor));
        }
    }

    private static final class CountingJob implements RecoverableJob {
        final List<String> executions = new ArrayList<>();

        @Override
        public String name() {
            return "upload";
        }

        @Override
        public String description() {
            return "Envío";
        }

        @Override
        public String cron() {
            return "0 0 13,18,21 * * *";
        }

        @Override
        public void execute() {
            executions.add("x");
        }
    }

    private static Clock clockAt(String local) {
        return Clock.fixed(LocalDateTime.parse(local).atZone(MADRID).toInstant(), MADRID);
    }

    @Test
    void aSuHoraSeEjecutaYQuedaRegistrado() {
        MemoryRegistry registry = new MemoryRegistry();
        CountingJob job = new CountingJob();
        registry.record("upload", LocalDateTime.parse("2026-11-13T13:00:00").atZone(MADRID).toInstant(),
                JobRunRegistry.Trigger.PROGRAMADO, LocalDateTime.parse("2026-11-13T13:00:00").atZone(MADRID).toInstant());

        boolean ran = new JobRunner(registry, clockAt("2026-11-13T18:00:00")).runIfDue(job, JobRunRegistry.Trigger.PROGRAMADO);

        assertThat(ran).isTrue();
        assertThat(job.executions).hasSize(1);
        assertThat(registry.runs.get("upload").lastTrigger()).isEqualTo(JobRunRegistry.Trigger.PROGRAMADO);
        assertThat(registry.runs.get("upload").lastScheduledFor())
                .isEqualTo(LocalDateTime.parse("2026-11-13T18:00:00").atZone(MADRID).toInstant());
    }

    @Test
    void siSeSaltoSuHoraElComprobadorLoEjecutaUnaVezYLaHoraNormalNoLoRepite() {
        MemoryRegistry registry = new MemoryRegistry();
        CountingJob job = new CountingJob();
        Instant yesterday18 = LocalDateTime.parse("2026-11-12T18:00:00").atZone(MADRID).toInstant();
        registry.record("upload", yesterday18, JobRunRegistry.Trigger.PROGRAMADO, yesterday18);

        // se enciende el PC a las 8:00: el envío de ayer a las 21:00 se recupera
        JobRunner morning = new JobRunner(registry, clockAt("2026-11-13T08:00:00"));
        assertThat(morning.runIfDue(job, JobRunRegistry.Trigger.RECUPERADO)).isTrue();
        assertThat(registry.runs.get("upload").lastTrigger()).isEqualTo(JobRunRegistry.Trigger.RECUPERADO);

        // diez minutos después, el comprobador no lo repite
        assertThat(new JobRunner(registry, clockAt("2026-11-13T08:10:00")).runIfDue(job, JobRunRegistry.Trigger.RECUPERADO)).isFalse();
        assertThat(job.executions).hasSize(1);

        // a las 13:00 se ejecuta con normalidad
        assertThat(new JobRunner(registry, clockAt("2026-11-13T13:00:00")).runIfDue(job, JobRunRegistry.Trigger.PROGRAMADO)).isTrue();
        assertThat(job.executions).hasSize(2);
    }

    @Test
    void siElComprobadorSeAdelantaALaHoraNormalElEnvioNoSeDuplica() {
        MemoryRegistry registry = new MemoryRegistry();
        CountingJob job = new CountingJob();
        Instant at13 = LocalDateTime.parse("2026-11-13T13:00:00").atZone(MADRID).toInstant();

        // el comprobador pasa justo a las 13:00:00 y lanza el envío...
        new JobRunner(registry, clockAt("2026-11-13T13:00:00")).runIfDue(job, JobRunRegistry.Trigger.RECUPERADO);
        // ...y cuando llega la ejecución programada de las 13:00, ya no lo repite
        boolean ranAgain = new JobRunner(registry, Clock.fixed(at13.plusMillis(500), MADRID)).runIfDue(job, JobRunRegistry.Trigger.PROGRAMADO);

        assertThat(ranAgain).isFalse();
        assertThat(job.executions).hasSize(1);
    }
}
