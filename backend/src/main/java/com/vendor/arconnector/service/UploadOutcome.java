package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.Gap;
import com.vendor.arconnector.activities.ValidationProblem;

import java.time.LocalDate;
import java.util.List;

/** Resultado de un intento de envío del fichero de imputaciones. */
public record UploadOutcome(
        Status status,
        LocalDate submissionDate,
        Long apiCallId,
        int dayCount,
        int rowCount,
        int excludedDays,
        List<ValidationProblem> problems,
        List<Gap> gaps,
        String error
) {
    public enum Status {
        /** Fichero enviado y aceptado (2xx). */
        SENT,
        /** Cuota diaria del canal agotada; no se ha llamado a la API. */
        SKIPPED_QUOTA,
        /** El fichero es idéntico al último enviado con éxito hoy; no se gasta cuota. */
        UNCHANGED,
        /** No hay ningún día que enviar en el periodo abierto. */
        NOTHING_TO_SEND,
        /** La API respondió error o no hubo conexión; la llamada cuenta para la cuota. */
        FAILED,
        /** No se pudo ni generar el fichero (p.ej. árbol de actividades vacío). */
        NOT_BUILT
    }

    public boolean sent() {
        return status == Status.SENT;
    }
}
