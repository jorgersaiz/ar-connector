package com.vendor.arconnector.feedback;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.domain.FeedbackRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackParserTest {

    private static final String HEADER = "date;person_id;task_level_1_code;task_level_2_code;task_level_3_code;"
            + "task_level_4_code;task_level_5_code;sda_project_id;jira_issue_id;non_sda_project;technology_detail;time;reason\n";

    @Test
    void leeFilasAceptadasRechazadasYDiasNoImputables() {
        List<FeedbackRow> rows = FeedbackParser.parse(SemicolonCsv.parse(HEADER
                + "2026-09-01;abc;01;01.01;01.01.01;01.01.01;01.01.01;SDATOOL-1;;;;480;\n"
                + "2026-09-02;abc;01;01.01;01.01.02;01.01.02;01.01.02;SDATOOL-1;ETR-9;;;60;APV_05\n"
                + "2026-09-03;abc;;;;;;;;;;;\n"
                + "2026-09-04;abc;;;;;;;;;;;APV_01\n"));

        assertThat(rows).hasSize(4);

        FeedbackRow ok = rows.get(0);
        assertThat(ok.getActivityDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(ok.leafTaskTypeCode()).isEqualTo("01.01.01");
        assertThat(ok.getTimeMinutes()).isEqualTo(480);
        assertThat(ok.isRejected()).isFalse();
        assertThat(ok.isNonWorkingDay()).isFalse();

        FeedbackRow ko = rows.get(1);
        assertThat(ko.isRejected()).isTrue();
        assertThat(ko.getReason()).isEqualTo("APV_05");
        assertThat(ko.getJiraIssueId()).isEqualTo("ETR-9");

        assertThat(rows.get(2).isNonWorkingDay()).isTrue();
        assertThat(rows.get(2).isRejected()).isFalse();
        assertThat(rows.get(3).isNonWorkingDay()).isTrue();
        assertThat(rows.get(3).getReason()).isEqualTo("APV_01");
    }

    @Test
    void sinColumnaReasonNoEsUnFicheroDeFeedback() {
        String activitiesHeader = HEADER.replace(";reason", "");
        assertThatThrownBy(() -> FeedbackParser.parse(SemicolonCsv.parse(activitiesHeader + "2026-09-01;abc;;;;;;;;;;\n")))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void unaFechaMalFormadaDescartaElFichero() {
        assertThatThrownBy(() -> FeedbackParser.parse(SemicolonCsv.parse(HEADER + "ayer;abc;;;;;;;;;;;\n")))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("línea 2");
    }
}
