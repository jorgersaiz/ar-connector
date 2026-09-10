package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Cliente de pruebas sobre el sistema de ficheros:
 *  - los catálogos se leen de &lt;dir&gt;/&lt;catalogo&gt;.csv,
 *  - los ficheros subidos se escriben en &lt;dir&gt;/outbox/ para poder inspeccionarlos,
 *  - el feedback se lee de &lt;dir&gt;/feedback/activities_&lt;fecha&gt;_feedback.csv
 *    (si no existe, se comporta como el 404 de BBVA: "todavía no hay fichero").
 *
 * Permite desarrollar y probar toda la cadena sin credenciales de BBVA.
 */
public class FileSystemActivityReportClient implements ActivityReportClient {

    private static final Logger log = LoggerFactory.getLogger(FileSystemActivityReportClient.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final Path directory;

    public FileSystemActivityReportClient(Path directory) {
        this.directory = directory;
    }

    @Override
    public byte[] downloadCatalogue(CatalogName catalogue) {
        return read(directory.resolve(catalogue.apiName() + ".csv"), null);
    }

    @Override
    public byte[] downloadActivitiesFeedback(LocalDate submissionDate) {
        return read(directory.resolve("feedback").resolve("activities_" + submissionDate + "_feedback.csv"), 404);
    }

    @Override
    public void uploadActivitiesFile(byte[] content, String fileName) {
        Path outbox = directory.resolve("outbox");
        Path target = outbox.resolve(LocalTime.now().format(TIME) + "_" + fileName);
        try {
            Files.createDirectories(outbox);
            Files.write(target, content);
        } catch (IOException e) {
            throw new ActivityReportClientException("No se ha podido escribir el fichero en " + target.toAbsolutePath(), e);
        }
        log.info("Modo FILESYSTEM: fichero de imputaciones escrito en {}", target.toAbsolutePath());
    }

    private static byte[] read(Path file, Integer statusIfMissing) {
        if (!Files.isRegularFile(file)) {
            throw new ActivityReportClientException("No existe el fichero local " + file.toAbsolutePath(), statusIfMissing, null);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ActivityReportClientException("No se ha podido leer " + file.toAbsolutePath(), e);
        }
    }
}
