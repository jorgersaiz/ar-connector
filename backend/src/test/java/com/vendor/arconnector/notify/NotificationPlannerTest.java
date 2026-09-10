package com.vendor.arconnector.notify;

import com.vendor.arconnector.domain.Notification.Kind;
import com.vendor.arconnector.notify.NotificationPlanner.Planned;
import com.vendor.arconnector.service.DashboardService.PersonStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPlannerTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-17");   // el corte de la 1ª quincena es el 19
    private static final String PANEL = "http://panel";

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static PersonStatus status(Long id, String uid, String name, List<LocalDate> gaps, LocalDate deadline,
                                       List<LocalDate> rejected, List<LocalDate> excluded, boolean hasTemplate) {
        return new PersonStatus(id, uid, name, uid.toLowerCase() + "@arktic.com", hasTemplate, gaps, deadline, rejected, excluded,
                excluded.isEmpty() ? List.of() : List.of("SDA_ENDED"));
    }

    private static final PersonStatus AL_DIA = status(1L, "XE1", "Ana", List.of(), null, List.of(), List.of(), true);
    private static final PersonStatus CON_HUECOS = status(2L, "XE2", "Bruno", List.of(d("2026-09-14"), d("2026-09-15")), d("2026-09-19"), List.of(), List.of(), false);
    private static final PersonStatus CON_RECHAZOS = status(3L, "XE3", "Carla", List.of(), null, List.of(d("2026-09-03")), List.of(), true);
    private static final PersonStatus HUECOS_LEJANOS = status(4L, "XE4", "Dani", List.of(d("2026-09-16")), d("2026-10-04"), List.of(), List.of(), true);

    @Test
    void recordatorioDeHuecosSoloCuandoElCorteEstaCerca() {
        List<Planned> planned = NotificationPlanner.plan(TODAY, 2, List.of(AL_DIA, CON_HUECOS, HUECOS_LEJANOS), List.of(), PANEL);

        assertThat(planned).hasSize(1);
        Planned p = planned.get(0);
        assertThat(p.kind()).isEqualTo(Kind.GAPS_REMINDER);
        assertThat(p.recipientEmail()).isEqualTo("xe2@arktic.com");
        assertThat(p.personId()).isEqualTo(2L);
        assertThat(p.deadline()).isEqualTo(d("2026-09-19"));
        assertThat(p.subject()).contains("2 día(s)").contains("19/09/2026");
        assertThat(p.body()).contains("14/09, 15/09").contains(PANEL + "/mi-mes").contains("plantilla por defecto");
    }

    @Test
    void conMasAntelacionTambienSeAvisaDeLosHuecosLejanos() {
        List<Planned> planned = NotificationPlanner.plan(TODAY, 20, List.of(HUECOS_LEJANOS), List.of(), PANEL);

        assertThat(planned).extracting(Planned::kind).containsExactly(Kind.GAPS_REMINDER);
    }

    @Test
    void losRechazosSeAvisanSiempre() {
        List<Planned> planned = NotificationPlanner.plan(TODAY, 2, List.of(CON_RECHAZOS), List.of(), PANEL);

        assertThat(planned).hasSize(1);
        assertThat(planned.get(0).kind()).isEqualTo(Kind.REJECTIONS);
        assertThat(planned.get(0).body()).contains("03/09");
    }

    @Test
    void resumenParaCadaManagerCuandoHayAlgoQueResolverYElCorteEstaCerca() {
        List<Planned> planned = NotificationPlanner.plan(TODAY, 2, List.of(AL_DIA, CON_HUECOS, CON_RECHAZOS),
                List.of("m1@arktic.com", "m2@arktic.com"), PANEL);

        List<Planned> summaries = planned.stream().filter(p -> p.kind() == Kind.MANAGER_SUMMARY).toList();
        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(Planned::recipientEmail).containsExactly("m1@arktic.com", "m2@arktic.com");
        assertThat(summaries.get(0).subject()).contains("2 persona(s)").contains("19/09/2026");
        assertThat(summaries.get(0).body()).contains("Bruno").contains("Carla").doesNotContain("Ana");
        assertThat(summaries.get(0).body()).contains("2 hueco(s)").contains("1 rechazado(s)");
    }

    @Test
    void sinNadaQueResolverNoHayAvisos() {
        assertThat(NotificationPlanner.plan(TODAY, 2, List.of(AL_DIA), List.of("m@arktic.com"), PANEL)).isEmpty();
    }

    @Test
    void soloHuecosLejanosNoGeneranResumenTodavia() {
        assertThat(NotificationPlanner.plan(TODAY, 2, List.of(HUECOS_LEJANOS), List.of("m@arktic.com"), PANEL)).isEmpty();
    }
}
