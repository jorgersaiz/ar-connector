package com.vendor.arconnector.feedback;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.CatalogRowException;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.domain.FeedbackRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee el fichero de feedback de imputaciones: "el mismo formato que el
 * fichero de imputaciones" más la columna "reason" (código APV_xx en los
 * rechazos). Lógica pura, sin Spring.
 */
public final class FeedbackParser {

    private FeedbackParser() {
    }

    public static List<FeedbackRow> parse(CsvTable table) {
        table.requireColumns("date", "person_id", "task_level_1_code", "task_level_5_code", "time", "reason");

        List<FeedbackRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (CsvRow row : table.rows()) {
            try {
                FeedbackRow r = new FeedbackRow();
                r.setLineNumber(row.lineNumber());
                r.setActivityDate(row.requiredDate("date"));
                r.setPersonId(row.requiredText("person_id"));
                r.setTaskLevel1Code(row.optionalText("task_level_1_code"));
                r.setTaskLevel2Code(row.optionalText("task_level_2_code"));
                r.setTaskLevel3Code(row.optionalText("task_level_3_code"));
                r.setTaskLevel4Code(row.optionalText("task_level_4_code"));
                r.setTaskLevel5Code(row.optionalText("task_level_5_code"));
                r.setSdaProjectId(row.optionalText("sda_project_id"));
                r.setJiraIssueId(row.optionalText("jira_issue_id"));
                r.setNonSdaProject(row.optionalText("non_sda_project"));
                r.setTimeMinutes(row.optionalInt("time"));
                r.setReason(row.optionalText("reason"));
                rows.add(r);
            } catch (CatalogRowException e) {
                errors.add(e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new CatalogParseException(errors);
        }
        return rows;
    }
}
