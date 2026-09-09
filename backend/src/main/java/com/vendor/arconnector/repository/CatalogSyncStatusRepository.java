package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.CatalogSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSyncStatusRepository extends JpaRepository<CatalogSyncStatus, String> {
}
