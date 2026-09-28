package com.vendor.arconnector.activities;

import com.vendor.arconnector.activities.MonthViewBuilder.DayView;
import com.vendor.arconnector.activities.MonthViewBuilder.Source;
import com.vendor.arconnector.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Vista de mes para el panel. Hoy es el jueves 10 de septiembre de 2026; el 1 de septiembre es martes. */
class MonthViewBuilderTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-10");

    private final Person ana = new Person("XE1", "ana@arktic.com", "Ana", "GPS001", "ES", LocalDate.parse("2026-01-01"));
    private final WorkCalendar calendar = new WorkCalendar(List.of(
            new Holiday("ES", LocalDate.parse("2026-09-08"), "Festivo local", HolidayScope.LOCAL),
            new Holiday("ES", LocalDate.parse("2026-09-11"), "Festivo nacional", HolidayScope.NATIONAL)));
    private final MonthViewBuilder builder = new MonthViewBuilder(new ModifiablePeriodPolicy(), calendar);

    private DefaultTemplate template() {
        DefaultTemplate t = new DefaultTemplate(ana, LocalDate.parse("2026-01-01"), null);
        t.addLine("01.01.01", "SDATOOL-1", null, null, 480);
        return t;
    }

    private static Map<LocalDate, DayView> byDate(List<DayView> views) {
        return views.stream().collect(Collectors.toMap(DayView::date, Function.identity()));
    }

    @Test
    void combinaDiasExplicitosPlantillaFestivosYFuturo() {
        ActivityDay vacaciones = ActivityDay.nonWorking(ana, LocalDate.parse("2026-09-04"), DayOrigin.MANUAL, "Vacaciones");
        vacaciones.setLastSentDate(LocalDate.parse("2026-09-09"));

        List<DayView> views = builder.build(ana, YearMonth.of(2026, 9), TODAY, List.of(vacaciones), List.of(template()));
        Map<LocalDate, DayView> d = byDate(views);

        assertThat(views).hasSize(30);

        DayView d1 = d.get(LocalDate.parse("2026-09-01"));
        assertThat(d1.source()).isEqualTo(Source.TEMPLATE);
        assertThat(d1.kind()).isEqualTo(DayKind.WORKED);
        assertThat(d1.totalMinutes()).isEqualTo(480);
        assertThat(d1.locked()).isFalse();
        assertThat(d1.lastSubmissionDate()).isEqualTo(LocalDate.parse("2026-09-18"));   // el 19 es sábado
        assertThat(d1.gap()).isFalse();

        DayView d4 = d.get(LocalDate.parse("2026-09-04"));
        assertThat(d4.source()).isEqualTo(Source.EXPLICIT);
        assertThat(d4.kind()).isEqualTo(DayKind.NON_WORKING);
        assertThat(d4.note()).isEqualTo("Vacaciones");
        assertThat(d4.lastSentDate()).isEqualTo(LocalDate.parse("2026-09-09"));

        DayView d5 = d.get(LocalDate.parse("2026-09-05"));   // sábado
        assertThat(d5.classification()).isEqualTo(DayClassification.WEEKEND);
        assertThat(d5.source()).isEqualTo(Source.NONE);

        DayView d8 = d.get(LocalDate.parse("2026-09-08"));
        assertThat(d8.source()).isEqualTo(Source.HOLIDAY);
        assertThat(d8.kind()).isEqualTo(DayKind.NON_WORKING);

        DayView d11 = d.get(LocalDate.parse("2026-09-11"));
        assertThat(d11.classification()).isEqualTo(DayClassification.NATIONAL_HOLIDAY);
        assertThat(d11.source()).isEqualTo(Source.NONE);
        assertThat(d11.future()).isTrue();

        DayView d15 = d.get(LocalDate.parse("2026-09-15"));   // futuro con plantilla
        assertThat(d15.source()).isEqualTo(Source.TEMPLATE);
        assertThat(d15.future()).isTrue();
        assertThat(d15.gap()).isFalse();
    }

    @Test
    void sinPlantillaLosLaborablesPasadosSonHuecosYLosFuturosNo() {
        Map<LocalDate, DayView> d = byDate(builder.build(ana, YearMonth.of(2026, 9), TODAY, List.of(), List.of()));

        assertThat(d.get(LocalDate.parse("2026-09-09")).gap()).isTrue();
        assertThat(d.get(LocalDate.parse("2026-09-10")).gap()).isTrue();
        assertThat(d.get(LocalDate.parse("2026-09-15")).gap()).isFalse();
        assertThat(d.get(LocalDate.parse("2026-09-05")).gap()).isFalse();   // sábado
        assertThat(d.get(LocalDate.parse("2026-09-08")).gap()).isFalse();   // festivo local: se envía como no imputable
    }

    @Test
    void losDiasCuyoPeriodoCerroEstanBloqueados() {
        Map<LocalDate, DayView> d = byDate(builder.build(ana, YearMonth.of(2026, 8), TODAY, List.of(), List.of(template())));

        DayView aug20 = d.get(LocalDate.parse("2026-08-20"));
        assertThat(aug20.locked()).isTrue();                    // 2ª quincena de agosto: último envío el 4 de septiembre
        assertThat(aug20.lastSubmissionDate()).isEqualTo(LocalDate.parse("2026-09-04"));
        assertThat(aug20.source()).isEqualTo(Source.TEMPLATE);  // se muestra lo que habría ido, aunque ya no se pueda tocar
    }

    @Test
    void fueraDelPerimetroNoHayNadaQueEnviarNiHuecos() {
        Person nueva = new Person("XE2", "b@arktic.com", "Bruno", "GPS001", "ES", LocalDate.parse("2026-09-15"));

        Map<LocalDate, DayView> d = byDate(builder.build(nueva, YearMonth.of(2026, 9), TODAY, List.of(), List.of()));

        assertThat(d.get(LocalDate.parse("2026-09-09")).gap()).isFalse();
        assertThat(d.get(LocalDate.parse("2026-09-09")).source()).isEqualTo(Source.NONE);
    }
}
