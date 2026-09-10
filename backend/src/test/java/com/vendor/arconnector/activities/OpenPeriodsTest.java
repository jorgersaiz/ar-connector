package com.vendor.arconnector.activities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPeriodsTest {

    private final ModifiablePeriodPolicy policy = new ModifiablePeriodPolicy();

    @Test
    void elDia17HayUnaSolaQuincenaAbiertaConSuCorte() {
        List<OpenPeriods.Period> periods = OpenPeriods.on(LocalDate.parse("2026-09-17"), policy);

        assertThat(periods).hasSize(2);
        OpenPeriods.Period first = periods.get(0);
        assertThat(first.label()).isEqualTo("1ª quincena de septiembre 2026");
        assertThat(first.from()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(first.to()).isEqualTo(LocalDate.parse("2026-09-15"));
        assertThat(first.lastSubmissionDate()).isEqualTo(LocalDate.parse("2026-09-19"));
        assertThat(first.daysLeft()).isEqualTo(2);

        OpenPeriods.Period second = periods.get(1);
        assertThat(second.label()).isEqualTo("2ª quincena de septiembre 2026");
        assertThat(second.to()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(second.lastSubmissionDate()).isEqualTo(LocalDate.parse("2026-10-04"));
    }

    @Test
    void elDia3IncluyeLaSegundaQuincenaDelMesAnterior() {
        List<OpenPeriods.Period> periods = OpenPeriods.on(LocalDate.parse("2026-10-03"), policy);

        assertThat(periods).extracting(OpenPeriods.Period::label)
                .containsExactly("2ª quincena de septiembre 2026", "1ª quincena de octubre 2026");
        assertThat(periods.get(0).daysLeft()).isEqualTo(1);
    }
}
