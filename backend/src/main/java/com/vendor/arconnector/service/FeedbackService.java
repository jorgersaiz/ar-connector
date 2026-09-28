package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.domain.FeedbackFile;
import com.vendor.arconnector.domain.FeedbackRow;
import com.vendor.arconnector.feedback.ActivityRowKeys;
import com.vendor.arconnector.feedback.FeedbackLocator;
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
import java.util.Optional;
import java.util.Set;

/**
 * Descarga el feedback de cada fichero de imputaciones enviado y lo aplica a
 * los días correspondientes (rechazos APV_xx por línea o por día).
 *
 * El feedback se publica a partir de las 3:00 del día de procesamiento. Como
 * no está claro si se pide con la fecha de envío o con la de procesamiento,
 * {@link FeedbackLocator} prueba ambas y comprueba por contenido que el
 * fichero corresponde a lo enviado. Si todavía no existe, se reintenta en la
 * siguiente ejecución.
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
        Optional<byte[]> sent = lastFileSentOn(submissionDate);
        if (sent.isEmpty()) {
            return new Result(submissionDate, Result.Status.FAILED, 0, 0, 0, 0,
                    "no consta ningún fichero enviado con éxito el " + submissionDate);
        }

        Optional<FeedbackLocator.Found> found;
        try {
            found = FeedbackLocator.locate(submissionDate, LocalDate.now(clock), this::download,
                    ActivityRowKeys::of, ActivityRowKeys.of(sent.get()));
        } catch (ActivityReportClientException e) {
            log.error("Error descargando el feedback del envío {}: {}", submissionDate, e.getMessage());
            return new Result(submissionDate, Result.Status.FAILED, 0, 0, 0, 0, e.getMessage());
        }
        if (found.isEmpty()) {
            log.info("Feedback del envío {} todavía no disponible", submissionDate);
            return new Result(submissionDate, Result.Status.NOT_AVAILABLE_YET, 0, 0, 0, 0, null);
        }

        byte[] content = found.get().content();
        List<FeedbackRow> rows = FeedbackParser.parse(SemicolonCsv.parse(content));

        FeedbackFile file = feedbackFileRepository.findBySubmissionDateWithRows(submissionDate)
                .orElseGet(() -> new FeedbackFile(submissionDate));
        file.setFeedbackDate(found.get().requestedDate());
        file.setDownloadedAt(Instant.now(clock));
        file.setContent(content);
        file.replaceRows(rows);

        List<ActivityDay> sentDays = activityDayRepository.findByLastSentDateWithLines(submissionDate);
        FeedbackMatcher.Result match = FeedbackMatcher.apply(rows, sentDays, submissionDate);
        file.setMatchedCount(match.matchedRows());

        feedbackFileRepository.save(file);
        activityDayRepository.saveAll(sentDays);

        if (match.unmatchedRows() > 0) {
            log.warn("Feedback del envío {}: {} fila(s) no casan con ningún día enviado", submissionDate, match.unmatchedRows());
        }
        log.info("Feedback del envío {} (publicado con fecha {}) aplicado: {} filas, {} rechazos, {} casadas, {} sin casar",
                submissionDate, found.get().requestedDate(), rows.size(), file.getRejectedCount(),
                match.matchedRows(), match.unmatchedRows());
        return new Result(submissionDate, Result.Status.APPLIED, rows.size(), file.getRejectedCount(),
                match.matchedRows(), match.unmatchedRows(), null);
    }

    /** Descarga el feedback de una fecha; vacío si Activity Report dice que no existe. */
    private Optional<byte[]> download(LocalDate date) {
        try {
            return Optional.of(client.downloadActivitiesFeedback(date));
        } catch (ActivityReportClientException e) {
            if (e.isFileNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Activity Report procesa el ÚLTIMO fichero recibido cada día: ese es el que hay que comparar. */
    private Optional<byte[]> lastFileSentOn(LocalDate submissionDate) {
        return quotaCallsOn(submissionDate).stream()
                .filter(ApiCall::isOk)
                .filter(c -> c.getContent() != null)
                .reduce((first, second) -> second)
                .map(ApiCall::getContent);
    }

    private List<ApiCall> quotaCallsOn(LocalDate day) {
        Instant from = day.atStartOfDay(clock.getZone()).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return apiCallRepository.findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(
                CallChannel.ACTIVITIES_UPLOAD, from, to);
    }
}
