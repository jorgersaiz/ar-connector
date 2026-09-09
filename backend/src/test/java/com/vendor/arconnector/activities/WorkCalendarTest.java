package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.Holiday;
import com.vendor.arconnector.domain.HolidayScope;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkCalendarTest {

    private final WorkCalendar calendar = new WorkCalendar(List.of(
            new Holiday("ES", LocalDate.parse("2026-05-01"), "Día del Trabajo", HolidayScope.NATIONAL),
            new Holiday("ES", LocalDate.parse("2026-05-15"), "San Isidro", HolidayScope.LOCAL)
    ));

    @Test
    void finDeSemanaNoSeEnvia() {
        assertThat(calendar.classify("ES", LocalDate.parse("2026-05-02"))).isEqualTo(DayClassification.WEEKEND); // sábado
        assertThat(calendar.classify("ES", LocalDate.parse("2026-05-03"))).isEqualTo(DayClassification.WEEKEND); // domingo
        assertThat(DayClassification.WEEKEND.isSkipped()).isTrue();
    }

    @Test
    void festivoNacionalNoSeEnvia() {
        assertThat(calendar.classify("ES", LocalDate.parse("2026-05-01"))).isEqualTo(DayClassification.NATIONAL_HOLIDAY);
        assertThat(DayClassification.NATIONAL_HOLIDAY.isSkipped()).isTrue();
    }

    @Test
    void festivoLocalSeEnviaComoDiaNoImputable() {
        assertThat(calendar.classify("ES", LocalDate.parse("2026-05-15"))).isEqualTo(DayClassification.LOCAL_HOLIDAY);
        assertThat(DayClassification.LOCAL_HOLIDAY.isSkipped()).isFalse();
    }

    @Test
    void diaLaborableNormal() {
        assertThat(calendar.classify("ES", LocalDate.parse("2026-05-04"))).isEqualTo(DayClassification.WORKING);
        assertThat(calendar.isWorkingDay("ES", LocalDate.parse("2026-05-04"))).isTrue();
    }

    @Test
    void losFestivosSonPorGeografia() {
        assertThat(calendar.classify("PT", LocalDate.parse("2026-05-15"))).isEqualTo(DayClassification.WORKING);
    }
}
