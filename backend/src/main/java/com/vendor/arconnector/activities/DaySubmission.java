package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DayKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Día completo de una persona, listo para volcar al fichero de imputaciones.
 * Un día NON_WORKING no lleva líneas y se envía como "día no imputable".
 */
public record DaySubmission(
        String uid,
        LocalDate date,
        DayKind kind,
        List<LineSubmission> lines
) {
    public DaySubmission {
        // El uid se compara y se hashea siempre en mayúsculas (igual que hace BBVA)
        uid = uid == null ? null : uid.trim().toUpperCase(java.util.Locale.ROOT);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static DaySubmission worked(String uid, LocalDate date, List<LineSubmission> lines) {
        return new DaySubmission(uid, date, DayKind.WORKED, lines);
    }

    public static DaySubmission nonWorking(String uid, LocalDate date) {
        return new DaySubmission(uid, date, DayKind.NON_WORKING, List.of());
    }

    public static DaySubmission from(ActivityDay day) {
        List<LineSubmission> lines = day.getLines().stream()
                .map(l -> new LineSubmission(l.getTaskTypeCode(), l.getSdaProjectId(), l.getJiraIssueId(),
                        l.getNonSdaProject(), l.getMinutes()))
                .toList();
        return new DaySubmission(day.getPerson().getUid(), day.getActivityDate(), day.getKind(), lines);
    }
}
