package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Nodo del catálogo "ÁRBOL DE ACTIVIDADES" (origen Activity Report).
 * Las columnas son exactamente las del documento v1.16.
 */
@Entity
@Table(name = "task_type")
@Getter
@Setter
@NoArgsConstructor
public class TaskType {

    @Id
    @Column(name = "task_type_id", length = 64)
    private String taskTypeId;

    @Column(name = "task_type_parent_id", length = 64)
    private String taskTypeParentId;

    @Column(name = "description_es", length = 500)
    private String descriptionEs;

    @Column(name = "description_en", length = 500)
    private String descriptionEn;

    @Column(name = "help_es", columnDefinition = "TEXT")
    private String helpEs;

    @Column(name = "help_en", columnDefinition = "TEXT")
    private String helpEn;

    @Column(name = "requires_sda_project", nullable = false)
    private boolean requiresSdaProject;

    @Column(name = "requires_jira_issue", nullable = false)
    private boolean requiresJiraIssue;

    @Column(name = "requires_non_sda_project", nullable = false)
    private boolean requiresNonSdaProject;

    /** Desde el 13/05/2026 ningún nodo debería tenerlo a true; informar technology_detail provoca rechazo. */
    @Column(name = "requires_technology_detail", nullable = false)
    private boolean requiresTechnologyDetail;

    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Imputable hasta ese día incluido; vacío si está vigente. */
    @Column(name = "disabled_date")
    private LocalDate disabledDate;

    /** Solo los nodos hoja (sin hijos) son imputables. */
    @Column(name = "can_be_selected", nullable = false)
    private boolean canBeSelected;

    @Column(name = "requires_evolutionary_sda", nullable = false)
    private boolean requiresEvolutionarySda;

    @Column(name = "requires_evolutionary_jira_issue", nullable = false)
    private boolean requiresEvolutionaryJiraIssue;

    @Column(name = "requires_conceptualization_time", nullable = false)
    private boolean requiresConceptualizationTime;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}
