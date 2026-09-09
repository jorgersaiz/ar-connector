package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemActivityReportClientTest {

    @Test
    void leeElFicheroConElNombreDelCatalogo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("countries.csv"), "country_code;ended_margin;onhold_margin\nESP;1;1\n");
        FileSystemActivityReportClient client = new FileSystemActivityReportClient(dir);

        byte[] content = client.downloadCatalogue(CatalogName.COUNTRIES);

        assertThat(new String(content, StandardCharsets.UTF_8)).startsWith("country_code;");
    }

    @Test
    void ficheroAusenteLanzaErrorDeCliente(@TempDir Path dir) {
        FileSystemActivityReportClient client = new FileSystemActivityReportClient(dir);

        assertThatThrownBy(() -> client.downloadCatalogue(CatalogName.TASK_TYPES))
                .isInstanceOf(ActivityReportClientException.class)
                .hasMessageContaining("task_types.csv");
    }

    @Test
    void laSubidaEscribeElFicheroEnLaBandejaDeSalida(@TempDir Path dir) throws Exception {
        FileSystemActivityReportClient client = new FileSystemActivityReportClient(dir);

        client.uploadActivitiesFile("date;person_id\n".getBytes(StandardCharsets.UTF_8), "activities_2026-09-09.csv");

        try (Stream<Path> files = Files.list(dir.resolve("outbox"))) {
            Path written = files.findFirst().orElseThrow();
            assertThat(written.getFileName().toString()).endsWith("_activities_2026-09-09.csv");
            assertThat(Files.readString(written)).isEqualTo("date;person_id\n");
        }
    }
}
