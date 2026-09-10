package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fichero de feedback de imputaciones descargado de Activity Report para una fecha de envío. */
@Entity
@Table(name = "feedback_file")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_date", nullable = false, unique = true)
    private LocalDate submissionDate;

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

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<FeedbackRow> rows = new ArrayList<>();

    public FeedbackFile(LocalDate submissionDate) {
        this.submissionDate = submissionDate;
    }

    public void replaceRows(List<FeedbackRow> newRows) {
        rows.clear();
        for (FeedbackRow r : newRows) {
            r.setFile(this);
            rows.add(r);
        }
        rowCount = rows.size();
        rejectedCount = (int) rows.stream().filter(FeedbackRow::isRejected).count();
    }
}
