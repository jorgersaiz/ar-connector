package com.vendor.arconnector.scheduler;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Cálculo de ejecuciones perdidas de un proceso programado. Lógica pura.
 *
 * Un proceso "toca" cuando su última hora programada (la más reciente que no
 * es futura) es posterior a su última ejecución. Solo se recupera esa última
 * hora, una vez: si el equipo estuvo apagado todo el fin de semana, el envío
 * se hace una vez al encenderlo, no una por cada hora perdida.
 */
public final class MissedRuns {

    /** Horizonte hacia atrás para buscar la última hora programada (las expresiones son diarias). */
    private static final int LOOKBACK_DAYS = 8;

    private MissedRuns() {
    }

    /** Última hora programada igual o anterior a {@code now}. */
    public static Optional<ZonedDateTime> lastScheduledAtOrBefore(String cron, ZonedDateTime now) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime cursor = now.minusDays(LOOKBACK_DAYS);
        ZonedDateTime last = null;
        while (true) {
            ZonedDateTime next = expression.next(cursor);
            if (next == null || next.isAfter(now)) {
                return Optional.ofNullable(last);
            }
            last = next;
            cursor = next;
        }
    }

    /** Próxima hora programada posterior a {@code now}. */
    public static Optional<ZonedDateTime> nextScheduledAfter(String cron, ZonedDateTime now) {
        return Optional.ofNullable(CronExpression.parse(cron).next(now));
    }

    /**
     * Hora programada que está pendiente de ejecutar, o vacío si no hay ninguna.
     *
     * @param lastRun     última ejecución registrada, o null si nunca se ha ejecutado
     * @param sameDayOnly true si una hora de un día anterior ya no se recupera (p. ej. avisos)
     */
    public static Optional<ZonedDateTime> due(String cron, Instant lastRun, ZonedDateTime now, boolean sameDayOnly) {
        Optional<ZonedDateTime> slot = lastScheduledAtOrBefore(cron, now);
        if (slot.isEmpty()) {
            return Optional.empty();
        }
        if (lastRun != null && !lastRun.isBefore(slot.get().toInstant())) {
            return Optional.empty();
        }
        if (sameDayOnly && !slot.get().toLocalDate().equals(now.toLocalDate())) {
            return Optional.empty();
        }
        return slot;
    }
}
