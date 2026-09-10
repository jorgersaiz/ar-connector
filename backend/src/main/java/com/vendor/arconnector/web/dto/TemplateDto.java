package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.DefaultTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Plantilla por defecto de una persona para un rango de fechas. */
public record TemplateDto(
        Long id,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @NotEmpty @Valid List<LineDto> lines
) {
    public static TemplateDto from(DefaultTemplate t) {
        return new TemplateDto(t.getId(), t.getValidFrom(), t.getValidTo(), t.getLines().stream().map(LineDto::from).toList());
    }
}
