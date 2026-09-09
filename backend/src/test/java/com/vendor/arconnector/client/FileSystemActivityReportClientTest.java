package com.vendor.arconnector.client;

import com.vendor.arconnector.catalog.CatalogName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
