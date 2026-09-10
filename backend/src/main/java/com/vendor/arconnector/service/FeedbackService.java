package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.domain.FeedbackFile;
import com.vendor.arconnector.domain.FeedbackRow;
import com.vendor.arconnector.feedback.FeedbackMatcher;
import com.vendor.arconnector.feedback.FeedbackParser;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.repository.FeedbackFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Descarga el feedback de cada fichero de imputaciones enviado y lo aplica a
 * los días correspondientes (rechazos APV_xx por línea o por día).
 *
 * El feedback de un envío del día N está disponible a partir de las 3:00 del
 * día N+1. Si todavía no existe (404), se reintenta en la siguiente ejecución.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** Cuántos días hacia atrás se buscan envíos sin feedback. */
    public static final int LOOKBACK_DAYS = 10;

    public record Result(LocalDate submissionDate, Status status, int rowCount, int rejectedCount,
                         int matchedRows, int unmatchedRows, String error) {
        public enum Status {
            APPLIED, NOT_AVAILABLE_YET, FAILED
        }
    }

    private final ActivityReportClient client;
    private final FeedbackFileRepository feedbackFileRepository;
    private final ActivityDayRepository activityDayRepository;
    private final ApiCallRepository apiCallRepository;
    private final Clock clock;

    public FeedbackService(ActivityReportClient client,
                           FeedbackFileRepository feedbackFileRepository,
                           ActivityDayRepository activityDayRepository,
                           ApiCallRepository apiCallRepository,
                           Clock clock) {
        this.client = client;
        this.feedbackFileRepository = feedbackFileRepository;
        this.activityDayRepository = activityDayRepository;
        this.apiCallRepository = apiCallRepository;
        this.clock = clock;
    }

    /** Fechas de envío recientes con llamada OK y sin feedback descargado todavía. */
    public List<LocalDate> pendingSubmissionDates() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        LocalDate to = today.minusDays(1);   // el de hoy no puede existir hasta mañana
        List<LocalDate> sent = apiCallRepository.findSuccessfulSubmissionDates(CallChannel.ACTIVITIES_UPLOAD, from, to);
        Set<LocalDate> done = new HashSet<>(feedbackFileRepository.findSubmissionDatesBetween(from, to));
        return sent.stream().filter(d -> !done.contains(d)).toList();
    }

    public List<Result> syncPending() {
        List<Result> results = new ArrayList<>();
        for (LocalDate date : pendingSubmissionDates()) {
            results.add(sync(date));
        }
        if (results.isEmpty()) {
            log.info("Feedback: no hay envíos pendientes de feedback");
        }
        return results;
    }

    @Transactional
    public Result sync(LocalDate submissionDate) {
        byte[] content;
        try {
            content = client.downloadActivitiesFeedback(submissionDate);
        } catch (ActivityReportClientException e) {
            if (e.getHttpStatus() != null && e.getHttpStatus() == 404) {
                log.info("Feedback del envío {} todavía no disponible", submissionDate);
                return new Result(submissionDate, Result.Status.NOT_AVAILABLE_YET, 0, 0, 0, 0, null);
            }
            log.error("Error descargando el feedback del envío {}: {}", submissionDate, e.getMessage());
            return new Result(submissionDate, Result.Status.FAILED, 0, 0, 0, 0, e.getMessage());
        }

        try {
            List<FeedbackRow> rows = FeedbackParser.parse(SemicolonCsv.parse(content));

            FeedbackFile file = feedbackFileRepository.findBySubmissionDateWithRows(submissionDate)
                    .orElseGet(() -> new FeedbackFile(submissionDate));
            file.setDownloadedAt(Instant.now(clock));
            file.setContent(content);
            file.replaceRows(rows);

            List<ActivityDay> sentDays = activityDayRepository.findByLastSentDateWithLines(submissionDate);
            FeedbackMatcher.Result match = FeedbackMatcher.apply(rows, sentDays, submissionDate);
            file.setMatchedCount(match.matchedRows());

            feedbackFileRepository.save(file);
            activityDayRepository.saveAll(sentDays);

            if (match.unmatchedRows() > 0) {
                log.warn("Feedback del envío {}: {} fila(s) no casan con ningún día enviado (¿imputaciones hechas a mano en Activity Report?)",
                        submissionDate, match.unmatchedRows());
            }
            log.info("Feedback del envío {} aplicado: {} filas, {} rechazos, {} casadas, {} sin casar",
                    submissionDate, rows.size(), file.getRejectedCount(), match.matchedRows(), match.unmatchedRows());
            return new Result(submissionDate, Result.Status.APPLIED, rows.size(), file.getRejectedCount(),
                    match.matchedRows(), match.unmatchedRows(), null);

        } catch (RuntimeException e) {
            log.error("Error procesando el feedback del envío {}: {}", submissionDate, e.getMessage());
            throw e;   // deshace la transacción; el job lo reintentará
        }
    }
}
