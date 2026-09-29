package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.ActivitiesFile;
import com.vendor.arconnector.activities.Gap;
import com.vendor.arconnector.activities.ValidationProblem;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.ApiCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActivitiesUploadServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate TODAY = LocalDate.parse("2026-09-09");
    private static final CallChannel CHANNEL = CallChannel.ACTIVITIES_UPLOAD;
    private static final byte[] CONTENT = "date;person_id;...\n2026-09-08;abc;03;03.01;03.01;03.01;03.01;;;;;480\n".getBytes(StandardCharsets.UTF_8);

    private PeriodSnapshotService snapshotService;
    private ActivityReportClient client;
    private CallQuotaService quotaService;
    private ApiCallRepository apiCallRepository;
    private ActivityDayRepository activityDayRepository;
    private ActivitiesUploadService service;

    @BeforeEach
    void setUp() {
        snapshotService = mock(PeriodSnapshotService.class);
        client = mock(ActivityReportClient.class);
        quotaService = mock(CallQuotaService.class);
        apiCallRepository = mock(ApiCallRepository.class);
        activityDayRepository = mock(ActivityDayRepository.class);
        service = new ActivitiesUploadService(snapshotService, client, quotaService, apiCallRepository, activityDayRepository, CLOCK);

        quota(0, 3, null);
        when(quotaService.callsOn(eq(CHANNEL), any())).thenReturn(List.of());
        when(apiCallRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void quota(int calls, int limit, Boolean lastOk) {
        when(quotaService.status(CHANNEL)).thenReturn(new CallQuotaService.QuotaStatus(CHANNEL, TODAY, calls, limit, lastOk));
    }

    private static PeriodSnapshotService.Result snapshot(byte[] content, int days, int rows, List<Long> ids) {
        return new PeriodSnapshotService.Result(new ActivitiesFile(content, days, rows),
                List.of(new Gap("XE9", LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-19"))),
                List.of(new ValidationProblem("XE8", LocalDate.parse("2026-09-07"), "SDA_REQUIRED", "x")),
                2, ids);
    }

    @Test
    void conLaCuotaAgotadaNoSeGeneraNiSeLlama() {
        quota(3, 3, true);

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.SKIPPED_QUOTA);
        verifyNoInteractions(snapshotService, client);
    }

    @Test
    void envioCorrectoRegistraLaLlamadaYMarcaLosDiasEnviados() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot(CONTENT, 5, 7, List.of(11L, 12L)));

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.SENT);
        assertThat(outcome.dayCount()).isEqualTo(5);
        assertThat(outcome.rowCount()).isEqualTo(7);
        assertThat(outcome.excludedDays()).isEqualTo(1);
        assertThat(outcome.gaps()).hasSize(1);

        verify(client).uploadActivitiesFile(CONTENT, "activities_2026-09-09.csv");

        ArgumentCaptor<ApiCall> saved = ArgumentCaptor.forClass(ApiCall.class);
        verify(apiCallRepository).save(saved.capture());
        assertThat(saved.getValue().isOk()).isTrue();
        assertThat(saved.getValue().getChannel()).isEqualTo(CHANNEL);
        assertThat(saved.getValue().getSubmissionDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getContent()).isEqualTo(CONTENT);
        assertThat(saved.getValue().getContentSha256()).isEqualTo(ActivitiesUploadService.sha256(CONTENT));

        verify(activityDayRepository).markSent(eq(List.of(11L, 12L)), eq(TODAY), any());
    }

    @Test
    void siLaApiFallaSeRegistraElKoYNoSeMarcanLosDias() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot(CONTENT, 5, 7, List.of(11L)));
        doThrow(new ActivityReportClientException("Activity Report respondió 400: cabecera incorrecta", 400, null))
                .when(client).uploadActivitiesFile(any(), any());

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.FAILED);
        assertThat(outcome.error()).contains("400");

        ArgumentCaptor<ApiCall> saved = ArgumentCaptor.forClass(ApiCall.class);
        verify(apiCallRepository).save(saved.capture());
        assertThat(saved.getValue().isOk()).isFalse();
        assertThat(saved.getValue().getHttpStatus()).isEqualTo(400);
        assertThat(saved.getValue().getContent()).isEqualTo(CONTENT);   // el fichero fallido también se conserva

        verify(activityDayRepository, never()).markSent(any(), any(), any());
    }

    @Test
    void siLaPeticionNoLlegaASalirNoSeRegistraLlamadaNiGastaCuota() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot(CONTENT, 5, 7, List.of(11L)));
        doThrow(ActivityReportClientException.notSent("No hay credencial de Activity Report configurada", null))
                .when(client).uploadActivitiesFile(any(), any());

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.FAILED);
        assertThat(outcome.error()).contains("credencial");
        assertThat(outcome.apiCallId()).isNull();
        verify(apiCallRepository, never()).save(any());
        verify(activityDayRepository, never()).markSent(any(), any(), any());
    }

    @Test
    void unFicheroIdenticoAlUltimoEnviadoHoyNoGastaCuota() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot(CONTENT, 5, 7, List.of(11L)));
        ApiCall previous = new ApiCall(CHANNEL, Instant.parse("2026-09-09T07:00:00Z"), TODAY, "f.csv", 5, 7,
                ActivitiesUploadService.sha256(CONTENT), CONTENT);
        previous.markOk(200);
        when(quotaService.callsOn(CHANNEL, TODAY)).thenReturn(List.of(previous));

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.UNCHANGED);
        verifyNoInteractions(client);
        verify(apiCallRepository, never()).save(any());
    }

    @Test
    void unFicheroIgualQueUnoQueFalloSiSeReenvia() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot(CONTENT, 5, 7, List.of(11L)));
        ApiCall previous = new ApiCall(CHANNEL, Instant.parse("2026-09-09T07:00:00Z"), TODAY, "f.csv", 5, 7,
                ActivitiesUploadService.sha256(CONTENT), CONTENT);
        previous.markFailed(503, "caído");
        when(quotaService.callsOn(CHANNEL, TODAY)).thenReturn(List.of(previous));

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.SENT);
        verify(client).uploadActivitiesFile(any(), any());
    }

    @Test
    void sinFilasNoSeLlamaALaApi() {
        when(snapshotService.buildFor(TODAY, true)).thenReturn(snapshot("date;...\n".getBytes(StandardCharsets.UTF_8), 0, 0, List.of()));

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.NOTHING_TO_SEND);
        verifyNoInteractions(client);
    }

    @Test
    void siNoSePuedeGenerarElFicheroSeInformaSinLlamar() {
        when(snapshotService.buildFor(TODAY, true)).thenThrow(new IllegalStateException("El árbol de actividades está vacío"));

        UploadOutcome outcome = service.uploadNow();

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.NOT_BUILT);
        assertThat(outcome.error()).contains("árbol");
        verifyNoInteractions(client);
    }
}
