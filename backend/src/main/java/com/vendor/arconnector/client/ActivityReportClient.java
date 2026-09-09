package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;

/**
 * Acceso a la API de vendors de Activity Report. Los servicios dependen de
 * esta interfaz, no de HTTP, para poder probar con ficheros locales.
 */
public interface ActivityReportClient {

    /** GET /catalogues/{catalogue}: contenido CSV del catálogo. */
    byte[] downloadCatalogue(CatalogName catalogue);

    /**
     * POST /upload_activities_file: sube el fichero de imputaciones.
     * El nombre es indiferente para BBVA ("solo se tendrá en cuenta el contenido").
     *
     * @throws ActivityReportClientException si la respuesta no es 2xx o no hay conexión
     */
    void uploadActivitiesFile(byte[] content, String fileName);
}
