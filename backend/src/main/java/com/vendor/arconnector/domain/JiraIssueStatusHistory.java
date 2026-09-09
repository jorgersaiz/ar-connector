package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Catálogo "HISTÓRICO DE ESTADOS DE ISSUES DE PROYECTOS SDA" (solo últimos 30 días). */
@Entity
@Table(name = "jira_issue_status_history")
@Getter
@Setter
@NoArgsConstructor
public class JiraIssueStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jira_issue_key", nullable = false, length = 64)
    private String jiraIssueKey;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "status_change_date", nullable = false)
    private LocalDate statusChangeDate;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public JiraIssueStatusHistory(String jiraIssueKey, String status, LocalDate statusChangeDate) {
        this.jiraIssueKey = jiraIssueKey;
        this.status = status;
        this.statusChangeDate = statusChangeDate;
    }
}
