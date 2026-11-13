package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.CatalogSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronización diaria de catálogos. El documento dice que "los ficheros de
 * catálogos estarán disponibles a diario a partir de las 3am", así que por
 * defecto se lanza a las 4:00 con una hora de margen.
 */
@Component
@Order(1)
public class CatalogSyncJob implements RecoverableJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private final CatalogSyncService catalogSyncService;
    private final JobRunner runner;
    private final String cron;

    public CatalogSyncJob(CatalogSyncService catalogSyncService, JobRunner runner,
                          @Value("${jobs.catalog-sync.cron:0 0 4 * * *}") String cron) {
        this.catalogSyncService = catalogSyncService;
        this.runner = runner;
        this.cron = cron;
    }

    @Scheduled(cron = "${jobs.catalog-sync.cron:0 0 4 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        runner.runIfDue(this, JobRunRegistry.Trigger.PROGRAMADO);
    }

    @Override
    public void execute() {
        log.info("Job de sincronización de catálogos: inicio");
        try {
            catalogSyncService.syncAll();
        } catch (RuntimeException e) {
            log.error("Job de sincronización de catálogos: error no controlado", e);
        }
    }

    @Override
    public String name() {
        return "catalog-sync";
    }

    @Override
    public String description() {
        return "Sincronización de catálogos";
    }

    @Override
    public String cron() {
        return cron;
    }
}
