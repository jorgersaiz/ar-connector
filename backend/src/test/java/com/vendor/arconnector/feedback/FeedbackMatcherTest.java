package com.vendor.arconnector.feedback;

import com.vendor.arconnector.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackMatcherTest {

    private static final LocalDate SENT = LocalDate.parse("2026-09-09");
    private final Person ana = new Person("XE1", "ana@arktic.com", "Ana", "GPS001", "ES", LocalDate.parse("2026-01-01"));

    private static FeedbackRow row(String date, String personId, String leaf, String sda, String jira, Integer minutes, String reason) {
        FeedbackRow r = new FeedbackRow();
        r.setActivityDate(LocalDate.parse(date));
        r.setPersonId(personId);
        if (leaf != null) {
            r.setTaskLevel1Code(leaf.substring(0, 2));
            r.setTaskLevel5Code(leaf);
        }
        r.setSdaProjectId(sda);
        r.setJiraIssueId(jira);
        r.setTimeMinutes(minutes);
        r.setReason(reason);
        return r;
    }

    @Test
    void casaCadaFilaConSuLineaYMarcaLosRechazos() {
        ActivityDay day = new ActivityDay(ana, LocalDate.parse("2026-09-01"), DayKind.WORKED, DayOrigin.MANUAL);
        ActivityLine build = day.addLine("01.01.01", "SDATOOL-1", null, null, 300);
        ActivityLine feature = day.addLine("01.01.02", "SDATOOL-1", "ETR-9", null, 180);
        day.setLastSentDate(SENT);

        FeedbackMatcher.Result result = FeedbackMatcher.apply(List.of(
                row("2026-09-01", ana.personId(), "01.01.01", "SDATOOL-1", null, 300, null),
                row("2026-09-01", ana.personId(), "01.01.02", "SDATOOL-1", "ETR-9", 180, "APV_05")
        ), List.of(day), SENT);

        assertThat(result.matchedRows()).isEqualTo(2);
        assertThat(result.unmatchedRows()).isZero();
        assertThat(result.rejectedRows()).isEqualTo(1);
        assertThat(build.getRejectReason()).isNull();
        assertThat(feature.getRejectReason()).isEqualTo("APV_05");
        assertThat(day.hasRejections()).isTrue();
        assertThat(day.getLastFeedbackDate()).isEqualTo(SENT);
    }

    @Test
    void unDiaNoImputableRechazadoSeMarcaEnElDia() {
        ActivityDay holiday = ActivityDay.nonWorking(ana, LocalDate.parse("2026-09-08"), DayOrigin.MANUAL, "Vacaciones");

        FeedbackMatcher.apply(List.of(row("2026-09-08", ana.personId(), null, null, null, null, "APV_01")), List.of(holiday), SENT);

        assertThat(holiday.getRejectReason()).isEqualTo("APV_01");
        assertThat(holiday.hasRejections()).isTrue();
    }

    @Test
    void unFeedbackSinReasonLimpiaElRechazoAnterior() {
        ActivityDay day = new ActivityDay(ana, LocalDate.parse("2026-09-01"), DayKind.WORKED, DayOrigin.MANUAL);
        ActivityLine line = day.addLine("01.01.01", "SDATOOL-1", null, null, 480);
        line.setRejectReason("APV_04");

        FeedbackMatcher.apply(List.of(row("2026-09-01", ana.personId(), "01.01.01", "SDATOOL-1", null, 480, null)), List.of(day), SENT);

        assertThat(line.getRejectReason()).isNull();
        assertThat(day.hasRejections()).isFalse();
    }

    @Test
    void filasQueNoCasanConNingunDiaOLineaSeDevuelvenComoNoCasadas() {
        ActivityDay day = new ActivityDay(ana, LocalDate.parse("2026-09-01"), DayKind.WORKED, DayOrigin.MANUAL);
        day.addLine("01.01.01", "SDATOOL-1", null, null, 480);

        FeedbackMatcher.Result result = FeedbackMatcher.apply(List.of(
                row("2026-09-01", "otro-hash", "01.01.01", "SDATOOL-1", null, 480, null),        // otra persona
                row("2026-09-01", ana.personId(), "01.01.01", "SDATOOL-2", null, 480, "APV_04")  // otro proyecto
        ), List.of(day), SENT);

        assertThat(result.matchedRows()).isZero();
        assertThat(result.unmatchedRows()).isEqualTo(2);
        assertThat(result.unmatched()).hasSize(2);
        assertThat(day.getLines().get(0).getRejectReason()).isNull();
    }
}
