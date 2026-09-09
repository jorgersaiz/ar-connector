package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Catálogo "HISTÓRICO DE FEATURES Y PROYECTOS SDA": cambios de asociación
 * feature -> SDA en la quincena en curso. Una feature con varios SDA aparece
 * en varias filas con la misma fecha.
 */
@Entity
@Table(name = "jira_issue_sda_project_history")
@Getter
@Setter
@NoArgsConstructor
public class JiraIssueSdaProjectHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jira_issue_key", nullable = false, length = 64)
    private String jiraIssueKey;

    @Column(name = "sda_project_id", nullable = false, length = 64)
    private String sdaProjectId;

    @Column(name = "change_date", nullable = false)
    private LocalDate changeDate;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public JiraIssueSdaProjectHistory(String jiraIssueKey, String sdaProjectId, LocalDate changeDate) {
        this.jiraIssueKey = jiraIssueKey;
        this.sdaProjectId = sdaProjectId;
        this.changeDate = changeDate;
    }
}
