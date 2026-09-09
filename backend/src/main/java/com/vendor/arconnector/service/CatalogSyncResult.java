package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogName;

/** Resultado de sincronizar un catálogo. */
public record CatalogSyncResult(CatalogName catalog, boolean success, int rowCount, String error, long millis) {

    public static CatalogSyncResult ok(CatalogName catalog, int rowCount, long millis) {
        return new CatalogSyncResult(catalog, true, rowCount, null, millis);
    }

    public static CatalogSyncResult failed(CatalogName catalog, String error, long millis) {
        return new CatalogSyncResult(catalog, false, 0, error, millis);
    }
}
