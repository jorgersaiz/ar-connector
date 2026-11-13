package com.vendor.arconnector.imports;

import java.time.LocalDate;

/**
 * Una fila del CSV de personas, ya validada.
 *
 * @param endDate           null = la columna venía vacía (al actualizar, se conserva la que hubiera)
 * @param needsRegistration null = la columna venía vacía (no se cambia el estado del alta)
 */
public record ImportedPerson(
        int lineNumber,
        String uid,
        String displayName,
        String email,
        String geography,
        String gpsCode,
        LocalDate startDate,
        LocalDate endDate,
        Boolean needsRegistration
) {
}
