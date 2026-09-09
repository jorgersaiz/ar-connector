package com.vendor.arconnector.activities;

import java.time.LocalDate;

/** Problema detectado al construir el fichero de imputaciones, antes de enviarlo a Activity Report. */
public record ValidationProblem(String uid, LocalDate date, String code, String message) {

    @Override
    public String toString() {
        return "[" + code + "] " + uid + " " + date + ": " + message;
    }
}
