package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Catálogo "PROYECTOS NO SDA". No tiene código: el identificador es el propio
 * texto, que es lo que se envía en la columna non_sda_project del fichero.
 */
@Entity
@Table(name = "non_sda_project")
@Getter
@Setter
@NoArgsConstructor
public class NonSdaProject {

    @Id
    @Column(name = "description")
    private String description;

    @Column(name = "disabled_date")
    private LocalDate disabledDate;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public NonSdaProject(String description, LocalDate disabledDate) {
        this.description = description;
        this.disabledDate = disabledDate;
    }
}
