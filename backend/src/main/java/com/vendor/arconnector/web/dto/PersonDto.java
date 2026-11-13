package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.RegistrationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PersonDto(
        Long id,
        @NotBlank String uid,
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotBlank String gpsCode,
        @NotBlank String geography,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean active,
        String personId,
        RegistrationStatus registrationStatus,
        LocalDate registrationSentDate,
        String registrationReason
) {
    public static PersonDto from(Person p) {
        return new PersonDto(p.getId(), p.getUid(), p.getEmail(), p.getDisplayName(), p.getGpsCode(), p.getGeography(),
                p.getStartDate(), p.getEndDate(), p.isActive(), p.personId(),
                p.getRegistrationStatus(), p.getRegistrationSentDate(), p.getRegistrationReason());
    }

    public void applyTo(Person p) {
        p.setUid(uid);
        p.setEmail(email.trim());
        p.setDisplayName(displayName.trim());
        p.setGpsCode(gpsCode.trim());
        p.setGeography(geography.trim().toUpperCase());
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        if (active != null) {
            p.setActive(active);
        }
        // Solo se puede pedir el alta (PENDING) o desistir (NOT_REQUIRED); SENT/REGISTERED los pone el conector
        if (registrationStatus == RegistrationStatus.PENDING || registrationStatus == RegistrationStatus.NOT_REQUIRED) {
            p.requestRegistration(registrationStatus == RegistrationStatus.PENDING);
        }
    }
}
