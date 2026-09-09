package com.vendor.arconnector.activities;

import com.vendor.arconnector.activities.TaskTree.Node;
import com.vendor.arconnector.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconstrucción del periodo abierto: personas x fechas modificables, con
 * plantilla por defecto, días explícitos, festivos y huecos.
 *
 * Escenario: envío el miércoles 18 de marzo de 2026. El 1 de marzo es domingo.
 * Días laborables del 2 al 18: 2,3,4,5,6, 9,10,11,12,13, 16,17,18 (13 días).
 * El 2 es festivo nacional y el 9 festivo local.
 */
class PeriodSnapshotBuilderTest {

    private static final LocalDate SUBMISSION = LocalDate.parse("2026-03-18");

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private final WorkCalendar calendar = new WorkCalendar(List.of(
            new Holiday("ES", d("2026-03-02"), "Festivo nacional de prueba", HolidayScope.NATIONAL),
            new Holiday("ES", d("2026-03-09"), "Festivo local de prueba", HolidayScope.LOCAL)
    ));

    private final PeriodSnapshotBuilder builder = new PeriodSnapshotBuilder(new ModifiablePeriodPolicy(), calendar);

    private static Person person(String uid, String startDate) {
        return new Person(uid, uid.toLowerCase() + "@vendor.com", "Persona " + uid, "GPS001", "ES", d(startDate));
    }

    private static DefaultTemplate template(Person p, String from) {
        DefaultTemplate t = new DefaultTemplate(p, d(from), null);
        t.addLine("01.01.01", "SDATOOL-1", null, null, 480);
        return t;
    }

    @Test
    void personaConPlantillaCubreTodosLosDiasLaborablesSinHuecos() {
        Person ana = person("XE1", "2025-01-01");
        ActivityDay vacaciones = ActivityDay.nonWorking(ana, d("2026-03-05"), DayOrigin.MANUAL, "Vacaciones");

        PeriodSnapshot snapshot = builder.build(SUBMISSION, List.of(ana), List.of(vacaciones), List.of(template(ana, "2025-01-01")));

        assertThat(snapshot.gaps()).isEmpty();
        assertThat(snapshot.days()).hasSize(12); // 13 laborables - 1 festivo nacional
        assertThat(snapshot.days()).extracting(DaySubmission::date).doesNotContain(d("2026-03-02"), d("2026-03-01"), d("2026-03-07"));

        // el día explícito se respeta tal cual
        DaySubmission dia5 = snapshot.days().stream().filter(x -> x.date().equals(d("2026-03-05"))).findFirst().orElseThrow();
        assertThat(dia5.kind()).isEqualTo(DayKind.NON_WORKING);

        // el festivo local sale como no imputable, generado por el conector
        DaySubmission dia9 = snapshot.days().stream().filter(x -> x.date().equals(d("2026-03-09"))).findFirst().orElseThrow();
        assertThat(dia9.kind()).isEqualTo(DayKind.NON_WORKING);

        // el resto viene de la plantilla
        DaySubmission dia10 = snapshot.days().stream().filter(x -> x.date().equals(d("2026-03-10"))).findFirst().orElseThrow();
        assertThat(dia10.kind()).isEqualTo(DayKind.WORKED);
        assertThat(dia10.lines()).containsExactly(LineSubmission.sda("01.01.01", "SDATOOL-1", 480));

        // 10 de plantilla + 1 festivo local se materializan para persistirlos; el explícito no
        assertThat(snapshot.generatedDays()).hasSize(11);
        assertThat(snapshot.generatedDays()).allMatch(day -> day.getOrigin() == DayOrigin.TEMPLATE);
    }

    @Test
    void personaSinPlantillaGeneraHuecosConSuFechaLimiteDeEnvio() {
        Person bruno = person("XE2", "2025-01-01");
        ActivityDay dia3 = new ActivityDay(bruno, d("2026-03-03"), DayKind.WORKED, DayOrigin.CSV_IMPORT);
        dia3.addLine("03.01", null, null, null, 480);

        PeriodSnapshot snapshot = builder.build(SUBMISSION, List.of(bruno), List.of(dia3), List.of());

        assertThat(snapshot.days()).hasSize(2); // el explícito + el festivo local
        assertThat(snapshot.gaps()).hasSize(10);
        assertThat(snapshot.gaps()).extracting(Gap::date).doesNotContain(d("2026-03-03"), d("2026-03-09"), d("2026-03-02"));

        Gap dia4 = snapshot.gaps().stream().filter(g -> g.date().equals(d("2026-03-04"))).findFirst().orElseThrow();
        assertThat(dia4.lastSubmissionDate()).isEqualTo(d("2026-03-19"));
        Gap dia17 = snapshot.gaps().stream().filter(g -> g.date().equals(d("2026-03-17"))).findFirst().orElseThrow();
        assertThat(dia17.lastSubmissionDate()).isEqualTo(d("2026-04-04"));
    }

    @Test
    void soloSeIncluyenDiasEnLosQueLaPersonaEstaEnPerimetro() {
        Person nueva = person("XE3", "2026-03-16");
        Person baja = person("XE4", "2025-01-01");
        baja.setActive(false);

        PeriodSnapshot snapshot = builder.build(SUBMISSION, List.of(nueva, baja), List.of(),
                List.of(template(nueva, "2026-03-16"), template(baja, "2025-01-01")));

        assertThat(snapshot.days()).extracting(DaySubmission::uid).containsOnly("XE3");
        assertThat(snapshot.days()).extracting(DaySubmission::date)
                .containsExactly(d("2026-03-16"), d("2026-03-17"), d("2026-03-18"));
        assertThat(snapshot.gaps()).isEmpty();
    }

    @Test
    void laPlantillaSoloAplicaDentroDeSuVigencia() {
        Person p = person("XE5", "2025-01-01");
        DefaultTemplate antigua = new DefaultTemplate(p, d("2025-01-01"), d("2026-03-10"));
        antigua.addLine("03.01", null, null, null, 480);
        DefaultTemplate nueva = new DefaultTemplate(p, d("2026-03-16"), null);
        nueva.addLine("01.01.01", "SDATOOL-9", null, null, 480);

        PeriodSnapshot snapshot = builder.build(SUBMISSION, List.of(p), List.of(), List.of(antigua, nueva));

        assertThat(snapshot.gaps()).extracting(Gap::date)
                .containsExactly(d("2026-03-11"), d("2026-03-12"), d("2026-03-13"));
        DaySubmission dia10 = snapshot.days().stream().filter(x -> x.date().equals(d("2026-03-10"))).findFirst().orElseThrow();
        assertThat(dia10.lines().get(0).taskTypeCode()).isEqualTo("03.01");
        DaySubmission dia16 = snapshot.days().stream().filter(x -> x.date().equals(d("2026-03-16"))).findFirst().orElseThrow();
        assertThat(dia16.lines().get(0).sdaProjectId()).isEqualTo("SDATOOL-9");
    }

    @Test
    void elSnapshotCompletoSeConvierteEnUnFicheroValido() {
        TaskTree tree = TaskTree.of(List.of(
                new Node("01", null, false, null, true, false, false, false),
                Node.branch("01.01", "01"),
                Node.leaf("01.01.01", "01.01")
        ));
        Person ana = person("XE1", "2025-01-01");
        Person bruno = person("XE2", "2025-01-01");

        PeriodSnapshot snapshot = builder.build(SUBMISSION, List.of(ana, bruno), List.of(),
                List.of(template(ana, "2025-01-01"), template(bruno, "2025-01-01")));
        ActivitiesFile file = new ActivitiesFileBuilder(tree, new ModifiablePeriodPolicy()).build(snapshot.days(), SUBMISSION);

        assertThat(file.dayCount()).isEqualTo(24);   // 12 días x 2 personas
        assertThat(file.rowCount()).isEqualTo(24);   // una línea por día trabajado y una fila por festivo local
        String[] lines = file.asText().split("\n");
        assertThat(lines).hasSize(25);
        assertThat(lines[0]).startsWith("date;person_id;");
        // festivo local (día 9) como día no imputable para ambas personas
        assertThat(file.asText()).contains("2026-03-09;" + ana.personId() + ";;;;;;;;;;");
        assertThat(file.asText()).contains("2026-03-09;" + bruno.personId() + ";;;;;;;;;;");
    }
}
