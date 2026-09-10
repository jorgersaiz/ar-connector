package com.vendor.arconnector.imports;

import com.vendor.arconnector.activities.DaySubmission;
import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.domain.DayKind;

import java.time.LocalDate;
import java.util.List;

/** Día completo de una persona leído del CSV de importación. */
public record ImportedDay(String uid, LocalDate date, DayKind kind, String note, List<LineSubmission> lines) {

    public DaySubmission toSubmission() {
        return new DaySubmission(uid, date, kind, lines);
    }
}
