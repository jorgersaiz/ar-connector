package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.Holiday;
import com.vendor.arconnector.domain.HolidayScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HolidayDto(
        Long id,
        @NotBlank String geography,
        @NotNull LocalDate date,
        @NotBlank String description,
        @NotNull HolidayScope scope
) {
    public static HolidayDto from(Holiday h) {
        return new HolidayDto(h.getId(), h.getGeography(), h.getDate(), h.getDescription(), h.getScope());
    }
}
