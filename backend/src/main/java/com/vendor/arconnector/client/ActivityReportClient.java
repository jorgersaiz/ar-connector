package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;

import java.time.LocalDate;

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

    /**
     * GET /feedback_file/{date}: feedback del fichero de actividades enviado en
     * esa fecha. Disponible a partir de las 3:00 del día siguiente.
     *
     * @throws ActivityReportClientException con httpStatus 404 si todavía no hay fichero
     */
    byte[] downloadActivitiesFeedback(LocalDate submissionDate);
}
