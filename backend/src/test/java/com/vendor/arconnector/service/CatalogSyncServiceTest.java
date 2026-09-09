package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.CatalogSyncStatus;
import com.vendor.arconnector.repository.CatalogSyncStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CatalogSyncServiceTest {

    private ActivityReportClient client;
    private CatalogStore store;
    private CatalogSyncStatusRepository statusRepository;
    private CatalogSyncService service;

    @BeforeEach
    void setUp() {
        client = mock(ActivityReportClient.class);
        store = mock(CatalogStore.class);
        statusRepository = mock(CatalogSyncStatusRepository.class);
        when(statusRepository.findById(any())).thenReturn(Optional.empty());
        service = new CatalogSyncService(client, store, statusRepository);
    }

    private static byte[] csv(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void sincronizacionCorrectaReemplazaElCatalogoYRegistraElExito() {
        when(client.downloadCatalogue(CatalogName.COUNTRIES))
                .thenReturn(csv("country_code;ended_margin;onhold_margin;sda_country;timezone\nESP;1;2;Spain;Europe/Madrid\n"));
        when(store.replace(eq(CatalogName.COUNTRIES), any(CsvTable.class))).thenReturn(1);

        CatalogSyncResult result = service.sync(CatalogName.COUNTRIES);

        assertThat(result.success()).isTrue();
        assertThat(result.rowCount()).isEqualTo(1);

        ArgumentCaptor<CatalogSyncStatus> saved = ArgumentCaptor.forClass(CatalogSyncStatus.class);
        verify(statusRepository).save(saved.capture());
        assertThat(saved.getValue().getCatalogName()).isEqualTo("countries");
        assertThat(saved.getValue().getLastSuccessAt()).isNotNull();
        assertThat(saved.getValue().getRowCount()).isEqualTo(1);
        assertThat(saved.getValue().getLastError()).isNull();
    }

    @Test
    void siFallaLaDescargaNoSeTocaLaBaseDeDatosYSeRegistraElError() {
        when(client.downloadCatalogue(CatalogName.SDA_PROJECTS))
                .thenThrow(new ActivityReportClientException("Activity Report respondió 503"));

        CatalogSyncResult result = service.sync(CatalogName.SDA_PROJECTS);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("503");
        verify(store, never()).replace(any(), any());

        ArgumentCaptor<CatalogSyncStatus> saved = ArgumentCaptor.forClass(CatalogSyncStatus.class);
        verify(statusRepository).save(saved.capture());
        assertThat(saved.getValue().getLastAttemptAt()).isNotNull();
        assertThat(saved.getValue().getLastSuccessAt()).isNull();
        assertThat(saved.getValue().getLastError()).contains("503");
    }

    @Test
    void unFicheroMalFormadoTambienSeRegistraComoErrorSinReemplazar() {
        when(client.downloadCatalogue(CatalogName.COUNTRIES)).thenReturn(csv("a;b\n1;2;3\n"));

        CatalogSyncResult result = service.sync(CatalogName.COUNTRIES);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("línea 2");
        verify(store, never()).replace(any(), any());
    }

    @Test
    void unErrorEnUnCatalogoNoDetieneLosDemas() {
        when(client.downloadCatalogue(any())).thenReturn(csv("x\n1\n"));
        when(client.downloadCatalogue(CatalogName.JIRA_ISSUES)).thenThrow(new ActivityReportClientException("caído"));
        when(store.replace(any(), any())).thenReturn(1);

        List<CatalogSyncResult> results = service.syncAll();

        assertThat(results).hasSize(CatalogName.values().length);
        assertThat(results).filteredOn(r -> !r.success()).extracting(CatalogSyncResult::catalog)
                .containsExactly(CatalogName.JIRA_ISSUES);
        verify(store, times(CatalogName.values().length - 1)).replace(any(), any());
    }

    @Test
    void elEstadoAnteriorSeReutilizaEnVezDeCrearUnoNuevo() {
        CatalogSyncStatus previous = new CatalogSyncStatus("countries");
        previous.setRowCount(99);
        when(statusRepository.findById("countries")).thenReturn(Optional.of(previous));
        when(client.downloadCatalogue(CatalogName.COUNTRIES)).thenThrow(new ActivityReportClientException("caído"));

        service.sync(CatalogName.COUNTRIES);

        verify(statusRepository).save(previous);
        assertThat(previous.getRowCount()).isEqualTo(99);   // el recuento del último éxito se conserva
        assertThat(previous.getLastError()).contains("caído");
    }
}
