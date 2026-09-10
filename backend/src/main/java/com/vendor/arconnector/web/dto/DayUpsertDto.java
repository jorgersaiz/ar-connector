package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.DayKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Alta o modificación de un día concreto (una excepción a la plantilla):
 * WORKED con sus líneas, o NON_WORKING (vacaciones, baja, festivo local) sin líneas.
 */
public record DayUpsertDto(
        @NotNull DayKind kind,
        String note,
        @Valid List<LineDto> lines
) {
    public List<LineDto> linesOrEmpty() {
        return lines == null ? List.of() : lines;
    }
}
