package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.domain.ActivityLine;
import com.vendor.arconnector.domain.DefaultTemplateLine;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Línea de actividad tal como la ve el panel: nodo hoja + proyecto/issue + minutos. */
public record LineDto(
        @NotBlank String taskTypeCode,
        String sdaProjectId,
        String jiraIssueId,
        String nonSdaProject,
        @Min(1) @Max(1440) int minutes,
        String rejectReason
) {
    public static LineDto from(ActivityLine l) {
        return new LineDto(l.getTaskTypeCode(), l.getSdaProjectId(), l.getJiraIssueId(), l.getNonSdaProject(),
                l.getMinutes(), l.getRejectReason());
    }

    public static LineDto from(DefaultTemplateLine l) {
        return new LineDto(l.getTaskTypeCode(), l.getSdaProjectId(), l.getJiraIssueId(), l.getNonSdaProject(),
                l.getMinutes(), null);
    }

    public LineSubmission toSubmission() {
        return new LineSubmission(taskTypeCode, blankToNull(sdaProjectId), blankToNull(jiraIssueId),
                blankToNull(nonSdaProject), minutes);
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
