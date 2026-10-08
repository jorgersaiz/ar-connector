package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.ActivitiesFile;
import com.vendor.arconnector.client.ActivityReportClient;
import com.vendor.arconnector.client.ActivityReportClientException;
import com.vendor.arconnector.config.UploadGate;
import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.ApiCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * Envío del fichero de imputaciones a Activity Report.
 *
 * Secuencia:
 *  1. Comprobar la cuota diaria del canal (3 llamadas si la última fue OK, 6 si KO).
 *  2. Reconstruir el periodo abierto completo (BBVA solo procesa el último
 *     fichero del día y sobreescribe días completos).
 *  3. Si el contenido es idéntico al último enviado con éxito hoy, no gastar cuota.
 *  4. Llamar a la API fuera de transacción y registrar la llamada (OK o KO)
 *     con el fichero exacto, para la cuota, la trazabilidad y el feedback.
 *  5. Si fue OK, marcar los días incluidos con la fecha de envío.
 */
@Service
public class ActivitiesUploadService {

    private static final Logger log = LoggerFactory.getLogger(ActivitiesUploadService.class);
    private static final CallChannel CHANNEL = CallChannel.ACTIVITIES_UPLOAD;

    private final PeriodSnapshotService snapshotService;
    private final ActivityReportClient client;
    private final CallQuotaService quotaService;
    private final ApiCallRepository apiCallRepository;
    private final ActivityDayRepository activityDayRepository;
    private final UploadGate uploadGate;
    private final Clock clock;

    public ActivitiesUploadService(PeriodSnapshotService snapshotService,
                                   ActivityReportClient client,
                                   CallQuotaService quotaService,
                                   ApiCallRepository apiCallRepository,
                                   ActivityDayRepository activityDayRepository,
                                   UploadGate uploadGate,
                                   Clock clock) {
        this.snapshotService = snapshotService;
        this.client = client;
        this.quotaService = quotaService;
        this.apiCallRepository = apiCallRepository;
        this.activityDayRepository = activityDayRepository;
        this.uploadGate = uploadGate;
        this.clock = clock;
    }

    /** Envío con la fecha de hoy en la zona horaria de Activity Report. */
    public UploadOutcome uploadNow() {
        return upload(LocalDate.now(clock));
    }

    public UploadOutcome upload(LocalDate submissionDate) {
        if (!uploadGate.isEnabled()) {
            log.warn("Envío de imputaciones omitido: {}", UploadGate.DISABLED_MESSAGE);
            return new UploadOutcome(UploadOutcome.Status.DISABLED, submissionDate, null, 0, 0, 0,
                    List.of(), List.of(), UploadGate.DISABLED_MESSAGE);
        }
        CallQuotaService.QuotaStatus quota = quotaService.status(CHANNEL);
        if (!quota.canCall()) {
            log.warn("Cuota diaria agotada para {}: {} llamadas hoy, límite {}", CHANNEL, quota.callsToday(), quota.limit());
            return new UploadOutcome(UploadOutcome.Status.SKIPPED_QUOTA, submissionDate, null, 0, 0, 0,
                    List.of(), List.of(), "cuota diaria agotada (" + quota.callsToday() + "/" + quota.limit() + ")");
        }

        PeriodSnapshotService.Result snapshot;
        try {
            snapshot = snapshotService.buildFor(submissionDate, true);
        } catch (RuntimeException e) {
            log.error("No se ha podido generar el fichero de imputaciones: {}", e.getMessage());
            return new UploadOutcome(UploadOutcome.Status.NOT_BUILT, submissionDate, null, 0, 0, 0,
                    List.of(), List.of(), e.getMessage());
        }

        ActivitiesFile file = snapshot.file();
        if (file.rowCount() == 0) {
            log.info("Nada que enviar a Activity Report para {}", submissionDate);
            return new UploadOutcome(UploadOutcome.Status.NOTHING_TO_SEND, submissionDate, null, 0, 0,
                    snapshot.problems().size(), snapshot.problems(), snapshot.gaps(), null);
        }

        String sha256 = sha256(file.content());
        boolean unchanged = quotaService.callsOn(CHANNEL, submissionDate).stream()
                .filter(ApiCall::isOk)
                .anyMatch(c -> sha256.equals(c.getContentSha256()));
        if (unchanged) {
            log.info("El fichero de imputaciones no ha cambiado desde el último envío correcto de hoy; no se gasta cuota");
            return new UploadOutcome(UploadOutcome.Status.UNCHANGED, submissionDate, null, file.dayCount(), file.rowCount(),
                    snapshot.problems().size(), snapshot.problems(), snapshot.gaps(), null);
        }

        String fileName = "activities_" + submissionDate + ".csv";
        ApiCall call = new ApiCall(CHANNEL, Instant.now(clock), submissionDate, fileName,
                file.dayCount(), file.rowCount(), sha256, file.content());
        try {
            client.uploadActivitiesFile(file.content(), fileName);
            call.markOk(200);
            recordSuccess(call, snapshot.includedDayIds(), submissionDate);
            log.info("Fichero de imputaciones enviado: {} días, {} filas, {} excluidos, {} huecos",
                    file.dayCount(), file.rowCount(), snapshot.problems().size(), snapshot.gaps().size());
            return new UploadOutcome(UploadOutcome.Status.SENT, submissionDate, call.getId(), file.dayCount(), file.rowCount(),
                    snapshot.problems().size(), snapshot.problems(), snapshot.gaps(), null);
        } catch (ActivityReportClientException e) {
            log.error("Fallo enviando el fichero de imputaciones: {}", e.getMessage());
            if (e.isNotSent()) {
                // la petición no ha salido: no es una llamada y no cuenta para la cuota
                return new UploadOutcome(UploadOutcome.Status.FAILED, submissionDate, null, file.dayCount(), file.rowCount(),
                        snapshot.problems().size(), snapshot.problems(), snapshot.gaps(), e.getMessage());
            }
            call.markFailed(e.getHttpStatus(), e.getMessage());
            apiCallRepository.save(call);
            return new UploadOutcome(UploadOutcome.Status.FAILED, submissionDate, call.getId(), file.dayCount(), file.rowCount(),
                    snapshot.problems().size(), snapshot.problems(), snapshot.gaps(), e.getMessage());
        }
    }

    /**
     * Sin @Transactional a propósito: es una llamada interna (no pasa por el
     * proxy de Spring) y cada operación ya lleva su propia transacción. Si
     * fallase el marcado de días, la llamada quedaría registrada igualmente,
     * que es lo que importa para la cuota.
     */
    private void recordSuccess(ApiCall call, List<Long> includedDayIds, LocalDate submissionDate) {
        apiCallRepository.save(call);
        if (!includedDayIds.isEmpty()) {
            activityDayRepository.markSent(includedDayIds, submissionDate, Instant.now(clock));
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
