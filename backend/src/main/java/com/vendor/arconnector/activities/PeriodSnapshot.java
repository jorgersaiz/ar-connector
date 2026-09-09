package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.ActivityDay;

import java.util.List;

/**
 * Resultado de reconstruir el periodo abierto:
 *  - {@code days}: todos los días a enviar (explícitos + generados de plantilla + festivos locales),
 *  - {@code generatedDays}: los días nuevos materializados desde plantilla o calendario, todavía sin persistir,
 *  - {@code gaps}: días laborables sin cubrir.
 */
public record PeriodSnapshot(
        List<DaySubmission> days,
        List<ActivityDay> generatedDays,
        List<Gap> gaps
) {
}
