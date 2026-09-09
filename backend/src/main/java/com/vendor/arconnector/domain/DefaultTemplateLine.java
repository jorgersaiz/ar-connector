package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "default_template_line")
@Getter
@Setter
@NoArgsConstructor
public class DefaultTemplateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_template_id", nullable = false)
    private DefaultTemplate template;

    @Column(name = "task_type_code", nullable = false, length = 64)
    private String taskTypeCode;

    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    @Column(name = "jira_issue_id", length = 64)
    private String jiraIssueId;

    @Column(name = "non_sda_project")
    private String nonSdaProject;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public DefaultTemplateLine(DefaultTemplate template, String taskTypeCode, String sdaProjectId,
                               String jiraIssueId, String nonSdaProject, int minutes) {
        this.template = template;
        this.taskTypeCode = taskTypeCode;
        this.sdaProjectId = sdaProjectId;
        this.jiraIssueId = jiraIssueId;
        this.nonSdaProject = nonSdaProject;
        this.minutes = minutes;
    }
}
