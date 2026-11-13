package com.vendor.arconnector.web;

import com.vendor.arconnector.scheduler.JobRunRegistry;
import com.vendor.arconnector.scheduler.MissedRuns;
import com.vendor.arconnector.scheduler.RecoverableJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Estado de los procesos programados, para la pantalla "Situación". */
@RestController
@RequestMapping("/api/jobs")
@PreAuthorize("hasRole('MANAGER')")
public class JobController {

    /**
     * @param lastTrigger PROGRAMADO o RECUPERADO; null si nunca se ha ejecutado
     * @param pending     true si se saltó su última hora y el comprobador lo lanzará en los próximos minutos
     */
    public record JobDto(String name, String description, String cron, Instant lastRunAt, String lastTrigger,
                         Instant lastScheduledFor, Instant nextRunAt, boolean pending) {
    }

    private final List<RecoverableJob> jobs;
    private final JobRunRegistry registry;
    private final Clock clock;

    public JobController(List<RecoverableJob> jobs, JobRunRegistry registry, Clock clock) {
        this.jobs = jobs;
        this.registry = registry;
        this.clock = clock;
    }

    @GetMapping
    public List<JobDto> list() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        Map<String, JobRunRegistry.JobRun> runs = registry.findAll().stream()
                .collect(Collectors.toMap(JobRunRegistry.JobRun::jobName, Function.identity()));
        return jobs.stream().map(job -> {
            JobRunRegistry.JobRun run = runs.get(job.name());
            Instant lastRun = run == null ? null : run.lastRunAt();
            return new JobDto(job.name(), job.description(), job.cron(), lastRun,
                    run == null ? null : run.lastTrigger().name(),
                    run == null ? null : run.lastScheduledFor(),
                    MissedRuns.nextScheduledAfter(job.cron(), now).map(ZonedDateTime::toInstant).orElse(null),
                    MissedRuns.due(job.cron(), lastRun, now, job.catchUpOnlySameDay()).isPresent());
        }).toList();
    }
}
