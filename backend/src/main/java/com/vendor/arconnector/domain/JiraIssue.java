package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Catálogo "ISSUES DE PROYECTOS SDA": features, tasks L1 y conceptualization
 * time de Jira.
 *
 * Una misma issue puede aparecer en varias filas, una por cada proyecto SDA al
 * que está asociada, y puede no tener ninguno (sda_project_id vacío). Por eso
 * la clave es un id propio y no el de la issue.
 */
@Entity
@Table(name = "jira_issue")
@Getter
@Setter
@NoArgsConstructor
public class JiraIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jira_issue_id", nullable = false, length = 64)
    private String jiraIssueId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    /** New, Analysing, In Progress, Test, Ready To Verify, Ready to Deploy, Deployed, Discarded, Blocked... */
    @Column(name = "status", length = 64)
    private String status;

    /** Área funcional ("Spain", "Global - Retail Client Solutions"...), no un código de país. */
    @Column(name = "geography", length = 64)
    private String geography;

    @Column(name = "disabled_date")
    private LocalDate disabledDate;

    /** Feature | Task | Conceptualization Time */
    @Column(name = "type", length = 64)
    private String type;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}
