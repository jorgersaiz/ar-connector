package com.vendor.arconnector.service;

import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.repository.FeedbackFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeedbackServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-10T05:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate SENT = LocalDate.parse("2026-09-09");
    private static final String HEADER = "date;person_id;task_level_1_code;task_level_2_code;task_level_3_code;"
            + "task_level_4_code;task_level_5_code;sda_project_id;jira_issue_id;non_sda_project;technology_detail;time;reason\n";

    private ActivityReportClient client;
    private FeedbackFileRepository feedbackFileRepository;
    private ActivityDayRepository activityDayRepository;
    private ApiCallRepository apiCallRepository;
    private FeedbackService service;

    private final Person ana = new Person("XE1", "ana@arktic.com", "Ana", "GPS001", "ES", LocalDate.parse("2026-01-01"));

    @BeforeEach
    void setUp() {
        client = mock(ActivityReportClient.class);
        feedbackFileRepository = mock(FeedbackFileRepository.class);
        activityDayRepository = mock(ActivityDayRepository.class);
        apiCallRepository = mock(ApiCallRepository.class);
        when(feedbackFileRepository.findBySubmissionDateWithRows(any())).thenReturn(Optional.empty());
        when(feedbackFileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new FeedbackService(client, feedbackFileRepository, activityDayRepository, apiCallRepository, CLOCK);
    }

    @Test
    void descargaAplicaYGuardaElFeedback() {
        ActivityDay day = new ActivityDay(ana, LocalDate.parse("2026-09-01"), DayKind.WORKED, DayOrigin.TEMPLATE);
        ActivityLine line = day.addLine("01.01.01", "SDATOOL-1", null, null, 480);
        when(activityDayRepository.findByLastSentDateWithLines(SENT)).thenReturn(List.of(day));
        when(client.downloadActivitiesFeedback(SENT)).thenReturn((HEADER
                + "2026-09-01;" + ana.personId() + ";01;01.01;01.01.01;01.01.01;01.01.01;SDATOOL-1;;;;480;APV_04\n")
                .getBytes(StandardCharsets.UTF_8));

        FeedbackService.Result result = service.sync(SENT);

        assertThat(result.status()).isEqualTo(FeedbackService.Result.Status.APPLIED);
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.matchedRows()).isEqualTo(1);
        assertThat(line.getRejectReason()).isEqualTo("APV_04");

        ArgumentCaptor<FeedbackFile> saved = ArgumentCaptor.forClass(FeedbackFile.class);
        verify(feedbackFileRepository).save(saved.capture());
        assertThat(saved.getValue().getSubmissionDate()).isEqualTo(SENT);
        assertThat(saved.getValue().getRows()).hasSize(1);
        assertThat(saved.getValue().getRows().get(0).isMatched()).isTrue();
        verify(activityDayRepository).saveAll(List.of(day));
    }

    @Test
    void un404SignificaQueTodaviaNoHayFeedback() {
        when(client.downloadActivitiesFeedback(SENT)).thenThrow(new ActivityReportClientException("no file", 404, null));

        FeedbackService.Result result = service.sync(SENT);

        assertThat(result.status()).isEqualTo(FeedbackService.Result.Status.NOT_AVAILABLE_YET);
        verify(feedbackFileRepository, never()).save(any());
    }

    @Test
    void otroErrorDeClienteSeInformaComoFallo() {
        when(client.downloadActivitiesFeedback(SENT)).thenThrow(new ActivityReportClientException("503", 503, null));

        assertThat(service.sync(SENT).status()).isEqualTo(FeedbackService.Result.Status.FAILED);
    }

    @Test
    void losEnviosPendientesSonLosOkRecientesSinFeedback() {
        when(apiCallRepository.findSuccessfulSubmissionDates(eq(CallChannel.ACTIVITIES_UPLOAD), any(), any()))
                .thenReturn(List.of(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-08"), SENT));
        when(feedbackFileRepository.findSubmissionDatesBetween(any(), any()))
                .thenReturn(List.of(LocalDate.parse("2026-09-07")));

        assertThat(service.pendingSubmissionDates()).containsExactly(LocalDate.parse("2026-09-08"), SENT);

        // el rango de búsqueda termina ayer: el envío de hoy no puede tener feedback todavía
        verify(apiCallRepository).findSuccessfulSubmissionDates(CallChannel.ACTIVITIES_UPLOAD,
                LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-09"));
    }
}
