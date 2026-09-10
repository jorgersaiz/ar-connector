package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Fila del fichero de feedback: una imputación procesada por Activity Report, con su motivo de rechazo si lo hubo. */
@Entity
@Table(name = "feedback_row")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_file_id", nullable = false)
    private FeedbackFile file;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "person_id", nullable = false, length = 128)
    private String personId;

    @Column(name = "task_level_1_code", length = 64)
    private String taskLevel1Code;

    @Column(name = "task_level_2_code", length = 64)
    private String taskLevel2Code;

    @Column(name = "task_level_3_code", length = 64)
    private String taskLevel3Code;

    @Column(name = "task_level_4_code", length = 64)
    private String taskLevel4Code;

    @Column(name = "task_level_5_code", length = 64)
    private String taskLevel5Code;

    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    @Column(name = "jira_issue_id", length = 64)
    private String jiraIssueId;

    @Column(name = "non_sda_project")
    private String nonSdaProject;

    @Column(name = "time_minutes")
    private Integer timeMinutes;

    @Column(name = "reason", length = 32)
    private String reason;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    public boolean isRejected() {
        return reason != null && !reason.isBlank();
    }

    /** Fila de día no imputable: sin actividad, proyectos ni tiempo. */
    public boolean isNonWorkingDay() {
        return (taskLevel1Code == null || taskLevel1Code.isBlank()) && timeMinutes == null;
    }

    /** Nodo hoja imputado (los niveles que faltan repiten el último, así que el 5º siempre es la hoja). */
    public String leafTaskTypeCode() {
        return taskLevel5Code;
    }
}
