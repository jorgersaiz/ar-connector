package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Catálogo "PAÍSES": márgenes de imputación (en trimestres) para SDA en estado Ended / On hold. */
@Entity
@Table(name = "country")
@Getter
@Setter
@NoArgsConstructor
public class Country {

    @Id
    @Column(name = "country_code", length = 8)
    private String countryCode;

    /** Qs de margen para proyectos Ended, contando desde el Q de end_date del SDA; null si el catálogo no lo informa. */
    @Column(name = "ended_margin")
    private Integer endedMargin;

    /** Qs de margen para proyectos On hold, contando desde el Q de last_modified_status_date; null si no se informa. */
    @Column(name = "onhold_margin")
    private Integer onholdMargin;

    /** Nombre del país tal como aparece en sda_project.country. */
    @Column(name = "sda_country", length = 128)
    private String sdaCountry;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}
