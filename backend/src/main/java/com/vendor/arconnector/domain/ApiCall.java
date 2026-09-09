package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Una llamada de subida a Activity Report, con el fichero exacto que se envió. */
@Entity
@Table(name = "api_call")
@Getter
@Setter
@NoArgsConstructor
public class ApiCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private CallChannel channel;

    @Column(name = "called_at", nullable = false)
    private Instant calledAt;

    @Column(name = "submission_date", nullable = false)
    private LocalDate submissionDate;

    @Column(name = "ok", nullable = false)
    private boolean ok;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "file_name", length = 128)
    private String fileName;

    @Column(name = "day_count")
    private Integer dayCount;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "content")
    private byte[] content;

    public ApiCall(CallChannel channel, Instant calledAt, LocalDate submissionDate, String fileName,
                   int dayCount, int rowCount, String contentSha256, byte[] content) {
        this.channel = channel;
        this.calledAt = calledAt;
        this.submissionDate = submissionDate;
        this.fileName = fileName;
        this.dayCount = dayCount;
        this.rowCount = rowCount;
        this.contentSha256 = contentSha256;
        this.content = content;
    }

    public void markOk(Integer httpStatus) {
        this.ok = true;
        this.httpStatus = httpStatus;
        this.errorMessage = null;
    }

    public void markFailed(Integer httpStatus, String errorMessage) {
        this.ok = false;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }
}
