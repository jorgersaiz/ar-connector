package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Catálogo "ISSUES DE PROYECTOS SDA": features, tasks L1 y conceptualization time de Jira. */
@Entity
@Table(name = "jira_issue")
@Getter
@Setter
@NoArgsConstructor
public class JiraIssue {

    @Id
    @Column(name = "jira_issue_id", length = 64)
    private String jiraIssueId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "geography", length = 16)
    private String geography;

    @Column(name = "disabled_date")
    private LocalDate disabledDate;

    /** feature | task | conceptualization time */
    @Column(name = "type", length = 64)
    private String type;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}
