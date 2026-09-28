package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.domain.CatalogSyncStatus;
import com.vendor.arconnector.repository.CatalogSyncStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sincronización de los catálogos que cede Activity Report: descarga, lectura,
 * reemplazo en base de datos y registro del resultado por catálogo.
 *
 * Cada catálogo es independiente: si uno falla (BBVA caído, formato
 * inesperado) se registra el error, se conserva la versión anterior y se
 * sigue con el siguiente.
 */
@Service
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

    /** Formato que produce nuestro PersonIdHasher; sirve para contrastarlo con los hashes que genera BBVA. */
    private static final Pattern OUR_PERSON_ID_FORMAT = Pattern.compile("[0-9a-fA-F]{64}");

    private final ActivityReportClient client;
    private final CatalogStore store;
    private final CatalogSyncStatusRepository statusRepository;

    public CatalogSyncService(ActivityReportClient client, CatalogStore store, CatalogSyncStatusRepository statusRepository) {
        this.client = client;
        this.store = store;
        this.statusRepository = statusRepository;
    }

    public List<CatalogSyncResult> syncAll() {
        List<CatalogSyncResult> results = new ArrayList<>();
        for (CatalogName catalog : CatalogName.values()) {
            results.add(sync(catalog));
        }
        long failed = results.stream().filter(r -> !r.success()).count();
        log.info("Sincronización de catálogos terminada: {} OK, {} con error", results.size() - failed, failed);
        return results;
    }

    public CatalogSyncResult sync(CatalogName catalog) {
        Instant start = Instant.now();
        CatalogSyncStatus status = statusRepository.findById(catalog.apiName())
                .orElseGet(() -> new CatalogSyncStatus(catalog.apiName()));
        status.setLastAttemptAt(start);

        try {
            byte[] content = client.downloadCatalogue(catalog);
            CsvTable table = SemicolonCsv.parse(content);
            checkAssumptions(catalog, table);
            int rows = store.replace(catalog, table);

            status.setLastSuccessAt(Instant.now());
            status.setRowCount(rows);
            status.setLastError(null);
            statusRepository.save(status);

            long millis = Duration.between(start, Instant.now()).toMillis();
            log.info("Catálogo {} sincronizado: {} filas en {} ms", catalog.apiName(), rows, millis);
            return CatalogSyncResult.ok(catalog, rows, millis);

        } catch (RuntimeException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            status.setLastError(error);
            statusRepository.save(status);

            long millis = Duration.between(start, Instant.now()).toMillis();
            log.error("Error sincronizando el catálogo {}: {}", catalog.apiName(), error);
            return CatalogSyncResult.failed(catalog, error, millis);
        }
    }

    /**
     * Avisos sobre suposiciones del conector que solo se pueden contrastar con
     * datos reales de BBVA. No bloquean la carga.
     */
    private void checkAssumptions(CatalogName catalog, CsvTable table) {
        if (catalog == CatalogName.HIDDEN_TASK_TYPES) {
            table.rows().stream()
                    .map(r -> r.text("person_id"))
                    .filter(id -> !id.isEmpty() && !OUR_PERSON_ID_FORMAT.matcher(id).matches())
                    .findFirst()
                    .ifPresent(id -> log.warn("El person_id '{}' del catálogo hidden_task_types no tiene el formato que genera "
                            + "PersonIdHasher (SHA256 hex en minúsculas). Revisar la anonimización antes de enviar imputaciones.", id));
        }
        if (catalog == CatalogName.TASK_TYPES) {
            long tech = table.rows().stream().filter(r -> r.text("requires_technology_detail").equalsIgnoreCase("true")).count();
            if (tech > 0) {
                log.warn("{} nodo(s) del árbol tienen requires_technology_detail = true; desde el 13/05/2026 no debería haber ninguno "
                        + "y el conector nunca informa technology_detail.", tech);
            }
        }
    }
}
