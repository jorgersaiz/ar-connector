package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Catálogo "NODOS OCULTOS DEL ÁRBOL": una persona (por su hash) no puede
 * imputar al nodo indicado en la fecha indicada. Cubre el periodo modificable
 * actual. Imputar ahí provoca rechazo APV_10.
 */
@Entity
@Table(name = "hidden_task_type")
@Getter
@Setter
@NoArgsConstructor
public class HiddenTaskType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hash del uid tal como lo genera Activity Report; sirve para verificar nuestro PersonIdHasher. */
    @Column(name = "person_id", nullable = false, length = 128)
    private String personId;

    @Column(name = "hidden_date", nullable = false)
    private LocalDate date;

    @Column(name = "geography", length = 16)
    private String geography;

    @Column(name = "task_type_code", nullable = false, length = 64)
    private String taskTypeCode;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public HiddenTaskType(String personId, LocalDate date, String geography, String taskTypeCode) {
        this.personId = personId;
        this.date = date;
        this.geography = geography;
        this.taskTypeCode = taskTypeCode;
    }
}
