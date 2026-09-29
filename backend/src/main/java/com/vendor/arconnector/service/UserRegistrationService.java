package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.PersonIdHasher;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.feedback.FeedbackLocator;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.registration.UserRegistrationFeedbackApplier;
import com.vendor.arconnector.registration.UserRegistrationFeedbackParser;
import com.vendor.arconnector.registration.UserRegistrationFile;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.repository.PersonRepository;
import com.vendor.arconnector.repository.UserRegistrationFeedbackRepository;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Registro de usuarios nuevos en Activity Report: envía las personas en
 * estado PENDING (cuota propia: 3 llamadas/día si la última fue OK, 6 si KO)
 * y aplica el feedback del día siguiente.
 */
@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final CallChannel CHANNEL = CallChannel.USER_REGISTRATION_UPLOAD;

    public record UploadResult(Status status, LocalDate submissionDate, int count, Long apiCallId, String error) {
        public enum Status {
            SENT, SKIPPED_QUOTA, NOTHING_TO_SEND, FAILED
        }
    }

    public record FeedbackResult(LocalDate submissionDate, Status status, int rowCount, int registered, int rejected,
                                 int unmatched, String error) {
        public enum Status {
            APPLIED, NOT_AVAILABLE_YET, FAILED
        }
    }

    private final PersonRepository personRepository;
    private final ActivityReportClient client;
    private final CallQuotaService quotaService;
    private final ApiCallRepository apiCallRepository;
    private final UserRegistrationFeedbackRepository feedbackRepository;
    private final Clock clock;

    public UserRegistrationService(PersonRepository personRepository, ActivityReportClient client, CallQuotaService quotaService,
                                   ApiCallRepository apiCallRepository, UserRegistrationFeedbackRepository feedbackRepository,
                                   Clock clock) {
        this.personRepository = personRepository;
        this.client = client;
        this.quotaService = quotaService;
        this.apiCallRepository = apiCallRepository;
        this.feedbackRepository = feedbackRepository;
        this.clock = clock;
    }

    public UploadResult uploadNow() {
        return upload(LocalDate.now(clock));
    }

    public UploadResult upload(LocalDate submissionDate) {
        List<Person> pending = personRepository.findByRegistrationStatus(RegistrationStatus.PENDING).stream()
                .filter(Person::isActive).toList();
        if (pending.isEmpty()) {
            return new UploadResult(UploadResult.Status.NOTHING_TO_SEND, submissionDate, 0, null, null);
        }
        CallQuotaService.QuotaStatus quota = quotaService.status(CHANNEL);
        if (!quota.canCall()) {
            log.warn("Cuota diaria agotada para {}: {} llamadas hoy, límite {}", CHANNEL, quota.callsToday(), quota.limit());
            return new UploadResult(UploadResult.Status.SKIPPED_QUOTA, submissionDate, pending.size(), null,
                    "cuota diaria agotada (" + quota.callsToday() + "/" + quota.limit() + ")");
        }

        UserRegistrationFile.Built file = UserRegistrationFile.build(pending.stream().map(Person::getUid).toList());
        String fileName = "users_" + submissionDate + ".csv";
        ApiCall call = new ApiCall(CHANNEL, Instant.now(clock), submissionDate, fileName, file.count(), file.count(),
                ActivitiesUploadService.sha256(file.content()), file.content());
        try {
            client.uploadUserRegistrationFile(file.content(), fileName);
            call.markOk(200);
            apiCallRepository.save(call);
            markSent(pending, submissionDate);
            log.info("Registro de usuarios enviado: {} persona(s)", file.count());
            return new UploadResult(UploadResult.Status.SENT, submissionDate, file.count(), call.getId(), null);
        } catch (ActivityReportClientException e) {
            log.error("Fallo enviando el registro de usuarios: {}", e.getMessage());
            if (e.isNotSent()) {
                // la petición no ha salido: no es una llamada y no cuenta para la cuota
                return new UploadResult(UploadResult.Status.FAILED, submissionDate, file.count(), null, e.getMessage());
            }
            call.markFailed(e.getHttpStatus(), e.getMessage());
            apiCallRepository.save(call);
            return new UploadResult(UploadResult.Status.FAILED, submissionDate, file.count(), call.getId(), e.getMessage());
        }
    }

    @Transactional
    protected void markSent(List<Person> persons, LocalDate submissionDate) {
        Instant now = Instant.now(clock);
        for (Person p : persons) {
            p.setRegistrationStatus(RegistrationStatus.SENT);
            p.setRegistrationSentDate(submissionDate);
            p.setRegistrationReason(null);
            p.setUpdatedAt(now);
        }
        personRepository.saveAll(persons);
    }

    private Optional<byte[]> downloadFeedback(LocalDate date) {
        try {
            return Optional.of(client.downloadUserRegistrationFeedback(date));
        } catch (ActivityReportClientException e) {
            if (e.isFileNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Hashes (en mayúsculas) de las personas que aparecen en un fichero de feedback de registro. */
    private static Set<String> hashesOf(byte[] content) {
        Set<String> hashes = new HashSet<>();
        for (UserRegistrationFeedbackParser.Row row : UserRegistrationFeedbackParser.parse(SemicolonCsv.parse(content))) {
            hashes.add(row.uid().isEmpty() ? PersonIdHasher.hash(row.uids()).toUpperCase(Locale.ROOT) : row.uid());
        }
        return hashes;
    }

    public List<LocalDate> pendingFeedbackDates() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(FeedbackService.LOOKBACK_DAYS);
        LocalDate to = today.minusDays(1);
        List<LocalDate> sent = apiCallRepository.findSuccessfulSubmissionDates(CHANNEL, from, to);
        Set<LocalDate> done = new HashSet<>(feedbackRepository.findSubmissionDatesBetween(from, to));
        return sent.stream().filter(d -> !done.contains(d)).toList();
    }

    public List<FeedbackResult> syncPendingFeedback() {
        List<FeedbackResult> results = new ArrayList<>();
        for (LocalDate date : pendingFeedbackDates()) {
            results.add(syncFeedback(date));
        }
        return results;
    }

    @Transactional
    public FeedbackResult syncFeedback(LocalDate submissionDate) {
        List<Person> sent = personRepository.findByRegistrationStatusAndRegistrationSentDate(RegistrationStatus.SENT, submissionDate);
        Set<String> sentHashes = new HashSet<>();
        sent.forEach(p -> sentHashes.add(p.personId().toUpperCase(Locale.ROOT)));

        // Igual que con las imputaciones: no está claro si el feedback se pide con la fecha de
        // envío o con la de procesamiento, así que se prueban y se comprueba por contenido.
        Optional<FeedbackLocator.Found> found;
        try {
            found = FeedbackLocator.locate(submissionDate, LocalDate.now(clock), this::downloadFeedback,
                    UserRegistrationService::hashesOf, sentHashes);
        } catch (ActivityReportClientException e) {
            return new FeedbackResult(submissionDate, FeedbackResult.Status.FAILED, 0, 0, 0, 0, e.getMessage());
        }
        if (found.isEmpty()) {
            return new FeedbackResult(submissionDate, FeedbackResult.Status.NOT_AVAILABLE_YET, 0, 0, 0, 0, null);
        }
        byte[] content = found.get().content();

        List<UserRegistrationFeedbackParser.Row> rows = UserRegistrationFeedbackParser.parse(SemicolonCsv.parse(content));
        UserRegistrationFeedbackApplier.Result applied = UserRegistrationFeedbackApplier.apply(rows, sent, submissionDate);
        personRepository.saveAll(sent);

        UserRegistrationFeedback file = feedbackRepository.findBySubmissionDate(submissionDate)
                .orElseGet(() -> new UserRegistrationFeedback(submissionDate));
        file.setFeedbackDate(found.get().requestedDate());
        file.setDownloadedAt(Instant.now(clock));
        file.setContent(content);
        file.setRowCount(rows.size());
        file.setRejectedCount(applied.rejected());
        file.setMatchedCount(applied.matched());
        feedbackRepository.save(file);

        if (applied.unmatched() > 0) {
            log.warn("Feedback de registro del {}: {} fila(s) no casan con ninguna persona enviada", submissionDate, applied.unmatched());
        }
        log.info("Feedback de registro del {} aplicado: {} registradas, {} rechazadas", submissionDate, applied.registered(), applied.rejected());
        return new FeedbackResult(submissionDate, FeedbackResult.Status.APPLIED, rows.size(), applied.registered(),
                applied.rejected(), applied.unmatched(), null);
    }
}
