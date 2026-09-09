package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Estado de la última sincronización de cada catálogo (para el panel y para alertar). */
@Entity
@Table(name = "catalog_sync_status")
@Getter
@Setter
@NoArgsConstructor
public class CatalogSyncStatus {

    @Id
    @Column(name = "catalog_name", length = 64)
    private String catalogName;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public CatalogSyncStatus(String catalogName) {
        this.catalogName = catalogName;
    }
}
