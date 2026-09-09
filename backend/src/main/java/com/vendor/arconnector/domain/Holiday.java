package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Festivo del calendario laboral de una geografía. */
@Entity
@Table(name = "holiday")
@Getter
@Setter
@NoArgsConstructor
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "geography", nullable = false, length = 8)
    private String geography;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private HolidayScope scope;

    public Holiday(String geography, LocalDate date, String description, HolidayScope scope) {
        this.geography = geography;
        this.date = date;
        this.description = description;
        this.scope = scope;
    }
}
