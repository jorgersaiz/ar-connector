package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cliente de pruebas que lee los catálogos de un directorio local
 * (&lt;dir&gt;/&lt;catalogo&gt;.csv). Permite desarrollar y probar toda la
 * sincronización sin credenciales de BBVA, y cargar ficheros de muestra
 * reales cuando los faciliten.
 */
public class FileSystemActivityReportClient implements ActivityReportClient {

    private final Path directory;

    public FileSystemActivityReportClient(Path directory) {
        this.directory = directory;
    }

    @Override
    public byte[] downloadCatalogue(CatalogName catalogue) {
        Path file = directory.resolve(catalogue.apiName() + ".csv");
        if (!Files.isRegularFile(file)) {
            throw new ActivityReportClientException("No existe el fichero local de catálogo " + file.toAbsolutePath());
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ActivityReportClientException("No se ha podido leer " + file.toAbsolutePath(), e);
        }
    }
}
