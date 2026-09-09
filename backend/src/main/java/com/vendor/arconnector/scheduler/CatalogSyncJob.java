package com.vendor.arconnector.scheduler;

import com.vendor.arconnector.service.CatalogSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronización diaria de catálogos. El documento dice que "los ficheros de
 * catálogos estarán disponibles a diario a partir de las 3am", así que por
 * defecto se lanza a las 4:00 con una hora de margen.
 */
@Component
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private final CatalogSyncService catalogSyncService;

    public CatalogSyncJob(CatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    @Scheduled(cron = "${jobs.catalog-sync.cron:0 0 4 * * *}", zone = "${jobs.timezone:Europe/Madrid}")
    public void run() {
        log.info("Job de sincronización de catálogos: inicio");
        try {
            catalogSyncService.syncAll();
        } catch (RuntimeException e) {
            log.error("Job de sincronización de catálogos: error no controlado", e);
        }
    }
}
