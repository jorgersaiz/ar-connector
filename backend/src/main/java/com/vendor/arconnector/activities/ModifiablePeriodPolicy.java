package com.vendor.arconnector.activities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodo modificable de Activity Report vía API, según el documento:
 *
 * <pre>
 *   Actividad de la 1ª quincena (días 1-15):  modificable del día 1 al día 20 del mismo mes.
 *   Actividad de la 2ª quincena (16-fin):     modificable del día 15 al día 5 del mes siguiente.
 * </pre>
 *
 * IMPORTANTE: la validación se hace sobre el día de PROCESAMIENTO, que es el
 * día siguiente al del envío (N+1). Por eso el último día real para enviar es
 * el 19 (1ª quincena) y el 4 del mes siguiente (2ª quincena).
 *
 * Además Activity Report no admite imputaciones a futuro.
 */
public final class ModifiablePeriodPolicy {

    public static final int FIRST_HALF_LAST_DAY = 15;
    public static final int FIRST_HALF_PERIOD_END_DAY = 20;
    public static final int SECOND_HALF_PERIOD_START_DAY = 15;
    public static final int SECOND_HALF_PERIOD_END_DAY_NEXT_MONTH = 5;

    /** Días que transcurren entre el envío del fichero y su procesamiento en Activity Report. */
    public static final int PROCESSING_LAG_DAYS = 1;

    /** Rango de fechas de procesamiento en las que una fecha de actividad se puede cargar. */
    public DateRange modifiablePeriodFor(LocalDate activityDate) {
        if (activityDate.getDayOfMonth() <= FIRST_HALF_LAST_DAY) {
            return new DateRange(
                    activityDate.withDayOfMonth(1),
                    activityDate.withDayOfMonth(FIRST_HALF_PERIOD_END_DAY));
        }
        return new DateRange(
                activityDate.withDayOfMonth(SECOND_HALF_PERIOD_START_DAY),
                activityDate.plusMonths(1).withDayOfMonth(SECOND_HALF_PERIOD_END_DAY_NEXT_MONTH));
    }

    /** true si un fichero procesado en {@code processingDate} puede cargar la fecha de actividad dada. */
    public boolean isModifiableOn(LocalDate activityDate, LocalDate processingDate) {
        return modifiablePeriodFor(activityDate).contains(processingDate);
    }

    /** Fecha de procesamiento que corresponde a un envío hecho en {@code submissionDate}. */
    public LocalDate processingDateFor(LocalDate submissionDate) {
        return submissionDate.plusDays(PROCESSING_LAG_DAYS);
    }

    /** true si un fichero ENVIADO en {@code submissionDate} puede cargar la fecha de actividad dada. */
    public boolean canSubmitOn(LocalDate activityDate, LocalDate submissionDate) {
        return !activityDate.isAfter(submissionDate)
                && !submissionDate.isAfter(lastSubmissionDate(activityDate));
    }

    /** Último día de procesamiento admitido para esa fecha de actividad (20 ó 5 del mes siguiente). */
    public LocalDate lastProcessingDate(LocalDate activityDate) {
        return modifiablePeriodFor(activityDate).to();
    }

    /**
     * Último día en el que se puede ENVIAR esa fecha de actividad: el 19 (1ª
     * quincena) o el 4 del mes siguiente (2ª quincena), adelantado al viernes
     * anterior si cae en fin de semana.
     *
     * Confirmado por BBVA (septiembre 2026): el procesamiento N+1 no corre en
     * fin de semana, así que si el 19 es sábado el último día de envío es el 18.
     * TODO: comprobar en el piloto si el fichero del viernes se procesa el
     * sábado o el lunes; si fuera el lunes, el límite real sería el jueves.
     */
    public LocalDate lastSubmissionDate(LocalDate activityDate) {
        LocalDate nominal = lastProcessingDate(activityDate).minusDays(PROCESSING_LAG_DAYS);
        return switch (nominal.getDayOfWeek()) {
            case SATURDAY -> nominal.minusDays(1);
            case SUNDAY -> nominal.minusDays(2);
            default -> nominal;
        };
    }

    /**
     * Todas las fechas de actividad (no futuras) que un envío hecho en
     * {@code submissionDate} todavía puede cargar. Es el "periodo abierto" que
     * el conector debe enviar completo en cada fichero.
     */
    public List<LocalDate> submittableActivityDates(LocalDate submissionDate) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = submissionDate.minusMonths(1).withDayOfMonth(1);
        while (!cursor.isAfter(submissionDate)) {
            if (canSubmitOn(cursor, submissionDate)) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }
}
