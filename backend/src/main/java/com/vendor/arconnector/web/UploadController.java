package com.vendor.arconnector.web;

import com.vendor.arconnector.activities.Gap;
import com.vendor.arconnector.activities.ValidationProblem;
import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.service.ActivitiesUploadService;
import com.vendor.arconnector.service.CallQuotaService;
import com.vendor.arconnector.service.PeriodSnapshotService;
import com.vendor.arconnector.service.UploadOutcome;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Envío de imputaciones: previsualizar el fichero del periodo abierto, forzar
 * un envío, consultar la cuota y el histórico de llamadas, y descargar el
 * fichero exacto que se envió.
 */
@RestController
@RequestMapping("/api/uploads")
@PreAuthorize("hasRole('MANAGER')")
public class UploadController {

    public record PreviewDto(LocalDate submissionDate, int dayCount, int rowCount, int excludedDays,
                             List<ValidationProblem> problems, List<Gap> gaps, String csv) {
    }

    public record ApiCallDto(Long id, CallChannel channel, Instant calledAt, LocalDate submissionDate, boolean ok,
                             Integer httpStatus, String errorMessage, String fileName, Integer dayCount, Integer rowCount) {
        static ApiCallDto from(ApiCall c) {
            return new ApiCallDto(c.getId(), c.getChannel(), c.getCalledAt(), c.getSubmissionDate(), c.isOk(),
                    c.getHttpStatus(), c.getErrorMessage(), c.getFileName(), c.getDayCount(), c.getRowCount());
        }
    }

    private final ActivitiesUploadService uploadService;
    private final PeriodSnapshotService snapshotService;
    private final CallQuotaService quotaService;
    private final ApiCallRepository apiCallRepository;
    private final Clock clock;

    public UploadController(ActivitiesUploadService uploadService, PeriodSnapshotService snapshotService,
                            CallQuotaService quotaService, ApiCallRepository apiCallRepository, Clock clock) {
        this.uploadService = uploadService;
        this.snapshotService = snapshotService;
        this.quotaService = quotaService;
        this.apiCallRepository = apiCallRepository;
        this.clock = clock;
    }

    /** Genera el fichero del periodo abierto sin enviarlo ni gastar cuota. */
    @GetMapping("/preview")
    public PreviewDto preview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate submissionDate = date == null ? LocalDate.now(clock) : date;
        PeriodSnapshotService.Result r = snapshotService.buildFor(submissionDate, false);
        return new PreviewDto(submissionDate, r.file().dayCount(), r.file().rowCount(), r.problems().size(),
                r.problems(), r.gaps(), r.file().asText());
    }

    /** Fuerza un envío ahora (respeta la cuota diaria). */
    @PostMapping("/run")
    public UploadOutcome run() {
        return uploadService.uploadNow();
    }

    @GetMapping("/quota")
    public CallQuotaService.QuotaStatus quota() {
        return quotaService.status(CallChannel.ACTIVITIES_UPLOAD);
    }

    @GetMapping
    public List<ApiCallDto> history() {
        return apiCallRepository.findTop50ByChannelOrderByCalledAtDesc(CallChannel.ACTIVITIES_UPLOAD)
                .stream().map(ApiCallDto::from).toList();
    }

    /** Fichero exacto que se envió en esa llamada. */
    @GetMapping(value = "/{id}/file", produces = "text/csv")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        ApiCall call = apiCallRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Llamada no encontrada: " + id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"" + call.getFileName() + "\"")
                .body(call.getContent() == null ? new byte[0] : call.getContent());
    }
}
