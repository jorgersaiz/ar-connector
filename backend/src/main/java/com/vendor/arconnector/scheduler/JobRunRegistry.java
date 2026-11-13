package com.vendor.arconnector.scheduler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Última ejecución de cada proceso programado (tabla job_run). */
@Repository
public class JobRunRegistry {

    public enum Trigger {
        /** Ejecutado a su hora. */
        PROGRAMADO,
        /** Ejecutado más tarde porque a su hora el equipo estaba apagado o la aplicación parada. */
        RECUPERADO
    }

    public record JobRun(String jobName, Instant lastRunAt, Trigger lastTrigger, Instant lastScheduledFor) {
    }

    private final JdbcTemplate jdbc;

    public JobRunRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<JobRun> find(String jobName) {
        return jdbc.query("select job_name, last_run_at, last_trigger, last_scheduled_for from job_run where job_name = ?",
                (rs, i) -> map(rs.getString(1), rs.getTimestamp(2), rs.getString(3), rs.getTimestamp(4)), jobName)
                .stream().findFirst();
    }

    public List<JobRun> findAll() {
        return jdbc.query("select job_name, last_run_at, last_trigger, last_scheduled_for from job_run",
                (rs, i) -> map(rs.getString(1), rs.getTimestamp(2), rs.getString(3), rs.getTimestamp(4)));
    }

    public void record(String jobName, Instant runAt, Trigger trigger, Instant scheduledFor) {
        jdbc.update("""
                insert into job_run (job_name, last_run_at, last_trigger, last_scheduled_for) values (?, ?, ?, ?)
                on conflict (job_name) do update
                   set last_run_at = excluded.last_run_at,
                       last_trigger = excluded.last_trigger,
                       last_scheduled_for = excluded.last_scheduled_for
                """, jobName, Timestamp.from(runAt), trigger.name(), Timestamp.from(scheduledFor));
    }

    private static JobRun map(String name, Timestamp runAt, String trigger, Timestamp scheduledFor) {
        return new JobRun(name, runAt.toInstant(), Trigger.valueOf(trigger), scheduledFor.toInstant());
    }
}
