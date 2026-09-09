package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;

/**
 * Acceso a la API de vendors de Activity Report. Los servicios dependen de
 * esta interfaz, no de HTTP, para poder probar con ficheros locales.
 */
public interface ActivityReportClient {

    /** GET /catalogues/{catalogue}: contenido CSV del catálogo. */
    byte[] downloadCatalogue(CatalogName catalogue);
}
