package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Línea de un día trabajado: actividad hoja del árbol + proyecto SDA / issue de
 * Jira / proyecto no SDA según lo exija la actividad + tiempo en minutos.
 */
@Entity
@Table(name = "activity_line")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_day_id", nullable = false)
    private ActivityDay day;

    /** Nodo hoja del árbol de actividades (can_be_selected = true). El conector calcula los 5 niveles. */
    @Column(name = "task_type_code", nullable = false, length = 64)
    private String taskTypeCode;

    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    @Column(name = "jira_issue_id", length = 64)
    private String jiraIssueId;

    @Column(name = "non_sda_project")
    private String nonSdaProject;

    /** Tiempo en minutos, entero entre 1 y 1440. */
    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Código APV_xx del último feedback de Activity Report si la línea fue rechazada. */
    @Column(name = "reject_reason", length = 32)
    private String rejectReason;

    public ActivityLine(ActivityDay day, String taskTypeCode, String sdaProjectId, String jiraIssueId,
                        String nonSdaProject, int minutes) {
        this.day = day;
        this.taskTypeCode = taskTypeCode;
        this.sdaProjectId = sdaProjectId;
        this.jiraIssueId = jiraIssueId;
        this.nonSdaProject = nonSdaProject;
        this.minutes = minutes;
    }
}
