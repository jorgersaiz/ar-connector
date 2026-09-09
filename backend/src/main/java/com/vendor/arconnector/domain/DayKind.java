package com.vendor.arconnector.domain;

/**
 * WORKED:      día con líneas de actividad (minutos por actividad/proyecto).
 * NON_WORKING: día no imputable (vacaciones, festivo local, baja...). Se envía
 *              a Activity Report como fila con solo fecha y person_id.
 */
public enum DayKind {
    WORKED,
    NON_WORKING
}
