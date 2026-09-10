package com.vendor.arconnector.domain;

import com.vendor.arconnector.activities.PersonIdHasher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persona del proveedor en perímetro de Activity Report.
 *
 * El {@code uid} es el código de usuario de BBVA sin anonimizar (hace falta
 * en claro para el fichero de registro de usuarios). Hacia el fichero de
 * imputaciones se envía siempre {@link #personId()}, el SHA256 del uid en
 * mayúsculas, tal como exige el documento.
 */
@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true, length = 64)
    private String uid;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Código GPS de la empresa a la que pertenece la persona (comunicado a BBVA). */
    @Column(name = "gps_code", nullable = false, length = 32)
    private String gpsCode;

    /** Geografía de la persona (ES, PT, MX...), comunicada a BBVA y usada para el calendario laboral. */
    @Column(name = "geography", nullable = false, length = 8)
    private String geography;

    /** Primer día imputable. Si la persona se registra vía API, es el día siguiente al del envío. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Alta en Activity Report vía API (solo personas que nunca han tenido acceso). */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 16)
    private RegistrationStatus registrationStatus = RegistrationStatus.NOT_REQUIRED;

    @Column(name = "registration_sent_date")
    private LocalDate registrationSentDate;

    @Column(name = "registration_reason", length = 32)
    private String registrationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Person(String uid, String email, String displayName, String gpsCode, String geography, LocalDate startDate) {
        setUid(uid);
        this.email = email;
        this.displayName = displayName;
        this.gpsCode = gpsCode;
        this.geography = geography;
        this.startDate = startDate;
    }

    /**
     * El uid se guarda siempre en mayúsculas: es como lo hashea BBVA y así
     * "xe123" y "XE123" no pueden convertirse en dos personas con el mismo person_id.
     */
    public void setUid(String uid) {
        this.uid = normalizeUid(uid);
    }

    public static String normalizeUid(String uid) {
        return uid == null ? null : uid.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** true si la persona está en perímetro (activa y dentro de su rango de fechas) ese día. */
    public boolean isInPerimeterOn(LocalDate date) {
        return active
                && !date.isBefore(startDate)
                && (endDate == null || !date.isAfter(endDate));
    }

    /** Identificador anonimizado que se envía a Activity Report. */
    public String personId() {
        return PersonIdHasher.hash(uid);
    }
}
