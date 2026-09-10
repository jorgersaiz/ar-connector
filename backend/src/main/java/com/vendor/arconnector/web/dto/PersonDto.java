package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.Person;
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
        String personId
) {
    public static PersonDto from(Person p) {
        return new PersonDto(p.getId(), p.getUid(), p.getEmail(), p.getDisplayName(), p.getGpsCode(), p.getGeography(),
                p.getStartDate(), p.getEndDate(), p.isActive(), p.personId());
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
    }
}
