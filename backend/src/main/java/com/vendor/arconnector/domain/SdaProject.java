package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Catálogo "PROYECTOS SDA" (origen Activity Report). */
@Entity
@Table(name = "sda_project")
@Getter
@Setter
@NoArgsConstructor
public class SdaProject {

    @Id
    @Column(name = "sda_project_id", length = 64)
    private String sdaProjectId;

    @Column(name = "description", length = 500)
    private String description;

    /** Q de inicio del proyecto, tal cual lo cede BBVA. Ej: "1Q 2022". */
    @Column(name = "start_quarter", length = 16)
    private String startQuarter;

    /** Q de fin del proyecto; a partir de él cuenta el margen "ended_margin" del país. */
    @Column(name = "end_quarter", length = 16)
    private String endQuarter;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "disabled_date")
    private LocalDate disabledDate;

    @Column(name = "country", length = 128)
    private String country;

    /** Último cambio de estado; a partir de su Q cuenta el margen "onhold_margin" del país. */
    @Column(name = "last_modified_status_date")
    private LocalDate lastModifiedStatusDate;

    @Column(name = "evolutionary_sda", nullable = false)
    private boolean evolutionarySda;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}
