package com.vendor.arconnector.feedback;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.ActivityLine;
import com.vendor.arconnector.domain.FeedbackRow;

import java.time.LocalDate;
import java.util.*;

/**
 * Aplica las filas del feedback a los días que se enviaron en ese fichero:
 * cada fila se casa con un día por (person_id, fecha) y, si es una línea de
 * actividad, con la línea por (nodo hoja, SDA, issue, proyecto no SDA).
 *
 * El resultado queda en {@code rejectReason} de la línea (o del día, para
 * días no imputables) y en {@code lastFeedbackDate} del día. Un feedback sin
 * reason limpia el rechazo anterior. Lógica pura, sin Spring.
 */
public final class FeedbackMatcher {

    public record Result(int matchedRows, int unmatchedRows, int rejectedRows, List<FeedbackRow> unmatched) {
    }

    private FeedbackMatcher() {
    }

    public static Result apply(List<FeedbackRow> rows, Collection<ActivityDay> sentDays, LocalDate submissionDate) {
        // Enviamos el hash en minúsculas y Activity Report lo devuelve en mayúsculas
        Map<String, ActivityDay> dayByKey = new HashMap<>();
        for (ActivityDay d : sentDays) {
            dayByKey.put(d.getPerson().personId().toUpperCase(Locale.ROOT) + "|" + d.getActivityDate(), d);
        }

        Set<ActivityDay> touched = new HashSet<>();
        List<FeedbackRow> unmatched = new ArrayList<>();
        int matched = 0;
        int rejected = 0;

        for (FeedbackRow row : rows) {
            ActivityDay day = dayByKey.get(row.getPersonId().toUpperCase(Locale.ROOT) + "|" + row.getActivityDate());
            if (day == null) {
                unmatched.add(row);
                continue;
            }
            if (touched.add(day)) {
                day.clearFeedback();
                day.setLastFeedbackDate(submissionDate);
            }

            if (row.isNonWorkingDay()) {
                day.setRejectReason(row.isRejected() ? row.getReason() : null);
                row.setMatched(true);
                matched++;
            } else {
                Optional<ActivityLine> line = day.getLines().stream()
                        .filter(l -> sameLine(l, row))
                        .findFirst();
                if (line.isEmpty()) {
                    unmatched.add(row);
                    continue;
                }
                line.get().setRejectReason(row.isRejected() ? row.getReason() : null);
                row.setMatched(true);
                matched++;
            }
            if (row.isRejected()) {
                rejected++;
            }
        }
        return new Result(matched, unmatched.size(), rejected, unmatched);
    }

    private static boolean sameLine(ActivityLine line, FeedbackRow row) {
        return eq(line.getTaskTypeCode(), row.leafTaskTypeCode())
                && eq(line.getSdaProjectId(), row.getSdaProjectId())
                && eq(line.getJiraIssueId(), row.getJiraIssueId())
                && eq(line.getNonSdaProject(), row.getNonSdaProject());
    }

    private static boolean eq(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return x.equals(y);
    }
}
