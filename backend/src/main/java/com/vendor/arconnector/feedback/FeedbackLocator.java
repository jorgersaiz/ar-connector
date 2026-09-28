package com.vendor.arconnector.feedback;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Localiza el feedback de un envío cuando no se sabe con qué fecha lo publica
 * Activity Report.
 *
 * En los ficheros de ejemplo de BBVA, el envío del 24/09 tiene un feedback
 * llamado "..._20260925_feedback.csv": la fecha del fichero es la de
 * PROCESAMIENTO (día siguiente, o el lunes si el envío fue en viernes), pero
 * el documento dice que se pide con "la fecha del fichero de actividades".
 *
 * En vez de suponer, se prueban la fecha de envío y los días siguientes, y se
 * acepta el primer fichero cuyo contenido corresponde a lo que se envió. Así
 * un fichero de otro envío nunca se aplica por error. Lógica pura, sin Spring.
 */
public final class FeedbackLocator {

    /** Días posteriores al envío que se prueban (cubre un fin de semana y un festivo). */
    public static final int MAX_DAYS_AFTER = 4;

    /** Parte de las filas del feedback que tienen que corresponder a lo enviado. */
    public static final double MIN_OVERLAP = 0.98;

    /**
     * @param requestedDate fecha con la que respondió Activity Report
     * @param content       fichero descargado
     */
    public record Found(LocalDate requestedDate, byte[] content) {
    }

    private FeedbackLocator() {
    }

    /** Fechas a probar, en orden: la de envío y las siguientes, sin pasar de hoy. */
    public static List<LocalDate> candidateDates(LocalDate submissionDate, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i <= MAX_DAYS_AFTER; i++) {
            LocalDate d = submissionDate.plusDays(i);
            if (!d.isAfter(today)) {
                dates.add(d);
            }
        }
        return dates;
    }

    /**
     * @param download  descarga el feedback de una fecha; vacío si Activity Report no tiene fichero para ella
     * @param keysOf    claves de las filas de un fichero de feedback (persona, fecha, línea...)
     * @param sentKeys  claves de lo que se envió
     */
    public static Optional<Found> locate(LocalDate submissionDate, LocalDate today,
                                         Function<LocalDate, Optional<byte[]>> download,
                                         Function<byte[], Set<String>> keysOf,
                                         Set<String> sentKeys) {
        for (LocalDate date : candidateDates(submissionDate, today)) {
            Optional<byte[]> content = download.apply(date);
            if (content.isEmpty()) {
                continue;
            }
            if (corresponds(keysOf.apply(content.get()), sentKeys)) {
                return Optional.of(new Found(date, content.get()));
            }
        }
        return Optional.empty();
    }

    /** true si el feedback y el envío contienen (casi) las mismas filas. */
    static boolean corresponds(Set<String> feedbackKeys, Set<String> sentKeys) {
        if (feedbackKeys.isEmpty() || sentKeys.isEmpty()) {
            return false;
        }
        long common = feedbackKeys.stream().filter(sentKeys::contains).count();
        int larger = Math.max(feedbackKeys.size(), sentKeys.size());
        return (double) common / larger >= MIN_OVERLAP;
    }
}
