package com.vendor.arconnector.activities;

import java.time.LocalDate;

/** Rango de fechas cerrado por ambos extremos. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Rango inválido: " + from + " > " + to);
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
