package com.vendor.arconnector.web;

import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.domain.CatalogSyncStatus;
import com.vendor.arconnector.repository.CatalogSyncStatusRepository;
import com.vendor.arconnector.service.CatalogSyncResult;
import com.vendor.arconnector.service.CatalogSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de los catálogos y sincronización manual (útil para no esperar al
 * job de las 4:00 durante las pruebas y cuando BBVA avise de un cambio).
 */
@RestController
@RequestMapping("/api/catalogs")
public class CatalogController {

    public record CatalogStatusDto(String catalog, Instant lastAttemptAt, Instant lastSuccessAt,
                                   Integer rowCount, String lastError) {
    }

    private final CatalogSyncService syncService;
    private final CatalogSyncStatusRepository statusRepository;

    public CatalogController(CatalogSyncService syncService, CatalogSyncStatusRepository statusRepository) {
        this.syncService = syncService;
        this.statusRepository = statusRepository;
    }

    @GetMapping
    public List<CatalogStatusDto> status() {
        Map<String, CatalogSyncStatus> byName = statusRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogSyncStatus::getCatalogName, Function.identity()));
        return Arrays.stream(CatalogName.values())
                .map(c -> {
                    CatalogSyncStatus s = byName.get(c.apiName());
                    return s == null
                            ? new CatalogStatusDto(c.apiName(), null, null, null, null)
                            : new CatalogStatusDto(c.apiName(), s.getLastAttemptAt(), s.getLastSuccessAt(), s.getRowCount(), s.getLastError());
                })
                .toList();
    }

    @PostMapping("/sync")
    public List<CatalogSyncResult> syncAll() {
        return syncService.syncAll();
    }

    @PostMapping("/{catalog}/sync")
    public CatalogSyncResult syncOne(@PathVariable String catalog) {
        CatalogName name = CatalogName.fromApiName(catalog)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo desconocido: " + catalog));
        return syncService.sync(name);
    }
}
