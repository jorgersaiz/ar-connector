package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Aviso enviado (o solo registrado) a una persona o a los managers. */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public enum Channel {
        LOG, EMAIL
    }

    public enum Kind {
        /** A la persona: días laborables sin imputar con el corte cerca. */
        GAPS_REMINDER,
        /** A la persona: días rechazados por Activity Report pendientes de corregir. */
        REJECTIONS,
        /** A los managers: resumen de huecos, rechazos y días excluidos antes del corte. */
        MANAGER_SUMMARY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private Kind kind;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "deadline")
    private LocalDate deadline;

    public Notification(Instant sentAt, Channel channel, Kind kind, String recipientEmail, Person person,
                        String subject, String body, LocalDate deadline) {
        this.sentAt = sentAt;
        this.channel = channel;
        this.kind = kind;
        this.recipientEmail = recipientEmail;
        this.person = person;
        this.subject = subject;
        this.body = body;
        this.deadline = deadline;
    }
}
