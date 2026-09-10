package com.vendor.arconnector.web;

import com.vendor.arconnector.domain.FeedbackFile;
import com.vendor.arconnector.domain.FeedbackRow;
import com.vendor.arconnector.repository.FeedbackFileRepository;
import com.vendor.arconnector.service.FeedbackService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Feedback de Activity Report: ficheros descargados, rechazos y sincronización manual. */
@RestController
@RequestMapping("/api/feedback")
@PreAuthorize("hasRole('MANAGER')")
public class FeedbackController {

    public record FeedbackFileDto(LocalDate submissionDate, Instant downloadedAt, int rowCount, int rejectedCount, int matchedCount) {
        static FeedbackFileDto from(FeedbackFile f) {
            return new FeedbackFileDto(f.getSubmissionDate(), f.getDownloadedAt(), f.getRowCount(), f.getRejectedCount(), f.getMatchedCount());
        }
    }

    public record FeedbackRowDto(int lineNumber, LocalDate activityDate, String personId, String taskTypeCode,
                                 String sdaProjectId, String jiraIssueId, String nonSdaProject, Integer timeMinutes,
                                 String reason, boolean matched) {
        static FeedbackRowDto from(FeedbackRow r) {
            return new FeedbackRowDto(r.getLineNumber(), r.getActivityDate(), r.getPersonId(), r.leafTaskTypeCode(),
                    r.getSdaProjectId(), r.getJiraIssueId(), r.getNonSdaProject(), r.getTimeMinutes(), r.getReason(), r.isMatched());
        }
    }

    private final FeedbackService feedbackService;
    private final FeedbackFileRepository feedbackFileRepository;

    public FeedbackController(FeedbackService feedbackService, FeedbackFileRepository feedbackFileRepository) {
        this.feedbackService = feedbackService;
        this.feedbackFileRepository = feedbackFileRepository;
    }

    @GetMapping
    public List<FeedbackFileDto> list() {
        return feedbackFileRepository.findTop60ByOrderBySubmissionDateDesc().stream().map(FeedbackFileDto::from).toList();
    }

    @GetMapping("/pending")
    public List<LocalDate> pending() {
        return feedbackService.pendingSubmissionDates();
    }

    /** Descarga y aplica el feedback de todos los envíos recientes que aún no lo tienen. */
    @PostMapping("/sync")
    public List<FeedbackService.Result> syncPending() {
        return feedbackService.syncPending();
    }

    /** Descarga (o vuelve a descargar) y aplica el feedback de un envío concreto. */
    @PostMapping("/{date}/sync")
    public FeedbackService.Result sync(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return feedbackService.sync(date);
    }

    @GetMapping("/{date}/rows")
    @Transactional(readOnly = true)
    public List<FeedbackRowDto> rows(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "true") boolean onlyRejected) {
        FeedbackFile file = feedbackFileRepository.findBySubmissionDateWithRows(date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay feedback descargado para el envío " + date));
        return file.getRows().stream()
                .filter(r -> !onlyRejected || r.isRejected())
                .map(FeedbackRowDto::from)
                .toList();
    }
}
