package com.vendor.arconnector.activities;

/** Qué es un día concreto para una geografía, de cara al fichero de imputaciones. */
public enum DayClassification {
    /** Día laborable: hay que enviar actividad (o plantilla por defecto, o queda como hueco). */
    WORKING,
    /** Fin de semana: no se envía nada (BBVA lo rechazaría con APV_01). */
    WEEKEND,
    /** Festivo que Activity Report ya conoce: no se envía nada. */
    NATIONAL_HOLIDAY,
    /** Festivo local que BBVA no conoce: se envía como día no imputable. */
    LOCAL_HOLIDAY;

    public boolean isSkipped() {
        return this == WEEKEND || this == NATIONAL_HOLIDAY;
    }
}
