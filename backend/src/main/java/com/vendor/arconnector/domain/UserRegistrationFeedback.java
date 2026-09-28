package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Fichero de feedback del registro de usuarios, descargado para una fecha de envío. */
@Entity
@Table(name = "user_registration_feedback")
@Getter
@Setter
@NoArgsConstructor
public class UserRegistrationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_date", nullable = false, unique = true)
    private LocalDate submissionDate;

    /** Fecha con la que Activity Report devolvió el fichero (la de envío o la de procesamiento). */
    @Column(name = "feedback_date")
    private LocalDate feedbackDate;

    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "content")
    private byte[] content;

    public UserRegistrationFeedback(LocalDate submissionDate) {
        this.submissionDate = submissionDate;
    }
}
