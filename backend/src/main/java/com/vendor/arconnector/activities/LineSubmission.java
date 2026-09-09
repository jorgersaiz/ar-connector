package com.vendor.arconnector.activities;

/** Línea de actividad lista para volcar al fichero (independiente de JPA). */
public record LineSubmission(
        String taskTypeCode,
        String sdaProjectId,
        String jiraIssueId,
        String nonSdaProject,
        int minutes
) {
    public static LineSubmission of(String taskTypeCode, int minutes) {
        return new LineSubmission(taskTypeCode, null, null, null, minutes);
    }

    public static LineSubmission sda(String taskTypeCode, String sdaProjectId, int minutes) {
        return new LineSubmission(taskTypeCode, sdaProjectId, null, null, minutes);
    }

    public static LineSubmission jira(String taskTypeCode, String sdaProjectId, String jiraIssueId, int minutes) {
        return new LineSubmission(taskTypeCode, sdaProjectId, jiraIssueId, null, minutes);
    }

    public static LineSubmission nonSda(String taskTypeCode, String nonSdaProject, int minutes) {
        return new LineSubmission(taskTypeCode, null, null, nonSdaProject, minutes);
    }
}
