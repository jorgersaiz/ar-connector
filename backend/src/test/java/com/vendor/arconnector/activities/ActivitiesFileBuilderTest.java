package com.vendor.arconnector.activities;

import com.vendor.arconnector.activities.TaskTree.Node;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Tests del fichero "IMPUTACIONES" contra las reglas del apartado
 * "Consideraciones" del documento v1.16.
 */
class ActivitiesFileBuilderTest {

    /** Árbol de ejemplo con la forma que describe el documento: "01" = proyectos SDA, "02" = no SDA. */
    private static final TaskTree TREE = TaskTree.of(List.of(
            new Node("01", null, false, null, true, false, false, false),
            Node.branch("01.01", "01"),
            Node.leaf("01.01.01", "01.01"),                                            // desarrollo en SDA
            new Node("01.01.02", "01.01", true, null, false, true, false, false),      // exige feature de Jira
            new Node("02", null, false, null, false, false, true, false),
            Node.leaf("02.01", "02"),                                                  // proyecto no SDA
            Node.branch("03", null),
            Node.leaf("03.01", "03"),                                                  // formación, sin proyecto
            new Node("03.02", "03", true, LocalDate.parse("2026-03-10"), false, false, false, false),
            new Node("03.04", "03", true, null, false, true, false, false)             // feature de evolutivo: issue sin SDA
    ));

    private static final LocalDate SUBMISSION = LocalDate.parse("2026-03-18");
    private static final String UID = "XE12345";
    private static final String PERSON_ID = PersonIdHasher.hash(UID);

    private final ActivitiesFileBuilder builder = new ActivitiesFileBuilder(TREE, new ModifiablePeriodPolicy());

    private static DaySubmission worked(String date, LineSubmission... lines) {
        return DaySubmission.worked(UID, LocalDate.parse(date), List.of(lines));
    }

    private ActivitiesFileValidationException failure(DaySubmission... days) {
        return catchThrowableOfType(ActivitiesFileValidationException.class,
                () -> builder.build(List.of(days), SUBMISSION));
    }

    // ---------------------------------------------------------------- formato

    @Test
    void cabeceraYSeparadorSonLosDelDocumento() {
        ActivitiesFile file = builder.build(List.of(), SUBMISSION);

        assertThat(file.asText()).isEqualTo(
                "date;person_id;task_level_1_code;task_level_2_code;task_level_3_code;task_level_4_code;task_level_5_code;"
                        + "sda_project_id;jira_issue_id;non_sda_project;technology_detail;time\n");
        assertThat(file.rowCount()).isZero();
    }

    @Test
    void filaDeDiaTrabajadoLlevaCincoNivelesUidAnonimizadoYMinutos() {
        ActivitiesFile file = builder.build(List.of(
                worked("2026-03-10", LineSubmission.sda("01.01.01", "SDATOOL-37040", 480))), SUBMISSION);

        assertThat(file.asText().split("\n")[1]).isEqualTo(
                "2026-03-10;" + PERSON_ID + ";01;01.01;01.01.01;01.01.01;01.01.01;SDATOOL-37040;;;;480");
        assertThat(file.dayCount()).isEqualTo(1);
        assertThat(file.rowCount()).isEqualTo(1);
    }

    @Test
    void diaNoImputableSoloLlevaFechaYPersonIdConElRestoVacio() {
        ActivitiesFile file = builder.build(List.of(
                DaySubmission.nonWorking(UID, LocalDate.parse("2026-03-10"))), SUBMISSION);

        String row = file.asText().split("\n")[1];
        assertThat(row).isEqualTo("2026-03-10;" + PERSON_ID + ";;;;;;;;;;");
        assertThat(row.split(";", -1)).hasSize(ActivitiesFileBuilder.HEADER.size());
    }

    @Test
    void unDiaConVariasLineasGeneraUnaFilaPorLinea() {
        ActivitiesFile file = builder.build(List.of(worked("2026-03-10",
                LineSubmission.sda("01.01.01", "SDATOOL-1", 300),
                LineSubmission.of("03.01", 180))), SUBMISSION);

        assertThat(file.rowCount()).isEqualTo(2);
        assertThat(file.asText()).contains(";03;03.01;03.01;03.01;03.01;;;;;180");
    }

    @Test
    void technologyDetailSiempreVaVacio() {
        ActivitiesFile file = builder.build(List.of(
                worked("2026-03-10", LineSubmission.of("03.01", 60))), SUBMISSION);

        String[] cols = file.asText().split("\n")[1].split(";", -1);
        assertThat(cols[10]).isEmpty();
    }

    @Test
    void lasFilasSalenOrdenadasPorFechaYPersona() {
        ActivitiesFile file = builder.build(List.of(
                DaySubmission.worked("XE2", LocalDate.parse("2026-03-11"), List.of(LineSubmission.of("03.01", 60))),
                DaySubmission.worked("XE1", LocalDate.parse("2026-03-11"), List.of(LineSubmission.of("03.01", 60))),
                DaySubmission.worked("XE1", LocalDate.parse("2026-03-10"), List.of(LineSubmission.of("03.01", 60)))
        ), SUBMISSION);

        String[] lines = file.asText().split("\n");
        assertThat(lines[1]).startsWith("2026-03-10;" + PersonIdHasher.hash("XE1"));
        assertThat(lines[2]).startsWith("2026-03-11;" + PersonIdHasher.hash("XE1"));
        assertThat(lines[3]).startsWith("2026-03-11;" + PersonIdHasher.hash("XE2"));
    }

    // ---------------------------------------------------------------- fechas

    @Test
    void rechazaFechasFuturas() {
        assertThat(failure(worked("2026-03-19", LineSubmission.of("03.01", 60))).hasCode("DATE_FUTURE")).isTrue();
    }

    @Test
    void rechazaFechasFueraDelPeriodoModificable() {
        // Febrero (2ª quincena) cerró el 4 de marzo; enviamos el 18
        ActivitiesFileValidationException ex = failure(worked("2026-02-20", LineSubmission.of("03.01", 60)));
        assertThat(ex.hasCode("DATE_OUT_OF_PERIOD")).isTrue();
        assertThat(ex.getProblems().get(0).message()).contains("2026-03-04");
    }

    @Test
    void elDia19AunAdmiteLaPrimeraQuincena() {
        ActivitiesFile file = new ActivitiesFileBuilder(TREE, new ModifiablePeriodPolicy())
                .build(List.of(worked("2026-03-02", LineSubmission.of("03.01", 60))), LocalDate.parse("2026-03-19"));
        assertThat(file.rowCount()).isEqualTo(1);
    }

    @Test
    void rechazaElMismoDiaDosVeces() {
        assertThat(failure(
                worked("2026-03-10", LineSubmission.of("03.01", 60)),
                DaySubmission.nonWorking(UID, LocalDate.parse("2026-03-10"))
        ).hasCode("DUPLICATE_DAY")).isTrue();
    }

    @Test
    void elUidSeNormalizaAMayusculasAsiQueDistintaCapitalizacionEsElMismoDia() {
        ActivitiesFileValidationException ex = failure(
                DaySubmission.worked("xe12345", LocalDate.parse("2026-03-10"), List.of(LineSubmission.of("03.01", 60))),
                DaySubmission.worked(" XE12345 ", LocalDate.parse("2026-03-10"), List.of(LineSubmission.of("03.01", 60))));

        assertThat(ex.hasCode("DUPLICATE_DAY")).isTrue();
        assertThat(ex.getProblems().get(0).uid()).isEqualTo("XE12345");
    }

    @Test
    void rechazaDiaTrabajadoSinLineas() {
        assertThat(failure(worked("2026-03-10")).hasCode("EMPTY_WORKED_DAY")).isTrue();
    }

    // ---------------------------------------------------------------- árbol

    @Test
    void rechazaActividadDesconocida() {
        assertThat(failure(worked("2026-03-10", LineSubmission.of("99.99", 60))).hasCode("TREE_NODE_UNKNOWN")).isTrue();
    }

    @Test
    void rechazaImputarANodosIntermedios() {
        assertThat(failure(worked("2026-03-10", LineSubmission.sda("01.01", "SDATOOL-1", 60)))
                .hasCode("TREE_NODE_NOT_SELECTABLE")).isTrue();
    }

    @Test
    void nodoDadoDeBajaEsImputableHastaSuDisabledDateIncluido() {
        ActivitiesFile ok = builder.build(List.of(worked("2026-03-10", LineSubmission.of("03.02", 60))), SUBMISSION);
        assertThat(ok.rowCount()).isEqualTo(1);

        assertThat(failure(worked("2026-03-11", LineSubmission.of("03.02", 60))).hasCode("TREE_NODE_DISABLED")).isTrue();
    }

    // ---------------------------------------------------------------- SDA / Jira / no SDA

    @Test
    void actividadDeProyectoSdaExigeSdaProjectId() {
        assertThat(failure(worked("2026-03-10", LineSubmission.of("01.01.01", 60))).hasCode("SDA_REQUIRED")).isTrue();
    }

    @Test
    void actividadSinProyectoNoAdmiteSdaProjectId() {
        assertThat(failure(worked("2026-03-10", LineSubmission.sda("03.01", "SDATOOL-1", 60))).hasCode("SDA_NOT_ALLOWED")).isTrue();
    }

    @Test
    void actividadConFeatureExigeJiraIssueYSuSda() {
        assertThat(failure(worked("2026-03-10", LineSubmission.sda("01.01.02", "SDATOOL-1", 60))).hasCode("JIRA_REQUIRED")).isTrue();

        ActivitiesFile ok = builder.build(List.of(
                worked("2026-03-10", LineSubmission.jira("01.01.02", "SDATOOL-1", "ETR-01", 60))), SUBMISSION);
        assertThat(ok.asText()).contains(";SDATOOL-1;ETR-01;;;60");
    }

    @Test
    void enActividadDeProyectoSdaLaIssueSinSdaSeRechazaPorFaltarElSda() {
        ActivitiesFileValidationException ex = failure(
                worked("2026-03-10", new LineSubmission("01.01.02", null, "ETR-01", null, 60)));
        assertThat(ex.getProblems()).extracting(ValidationProblem::code).containsExactly("SDA_REQUIRED");
    }

    @Test
    void featureDeEvolutivoVaConIssueYSinSda() {
        // Caso real: "03.04.02 Features de evolutivo" exige issue pero no cuelga de proyectos SDA
        ActivitiesFile ok = builder.build(List.of(
                worked("2026-03-10", new LineSubmission("03.04", null, "EVO-147", null, 480))), SUBMISSION);
        assertThat(ok.asText()).contains(";03;03.04;03.04;03.04;03.04;;EVO-147;;;480");

        assertThat(failure(worked("2026-03-10", LineSubmission.of("03.04", 480))).hasCode("JIRA_REQUIRED")).isTrue();
    }

    @Test
    void dondeLaActividadNoExigeIssueActivityReportLaAceptaYNoLaValida() {
        // Comprobado con el feedback real: 206 líneas así fueron aceptadas
        ActivitiesFile ok = builder.build(List.of(
                worked("2026-03-10", LineSubmission.jira("01.01.01", "SDATOOL-1", "ETR-01", 60))), SUBMISSION);
        assertThat(ok.asText()).contains(";SDATOOL-1;ETR-01;;;60");
    }

    @Test
    void actividadNoSdaExigeTextoDeProyectoNoSda() {
        assertThat(failure(worked("2026-03-10", LineSubmission.of("02.01", 60))).hasCode("NON_SDA_REQUIRED")).isTrue();

        ActivitiesFile ok = builder.build(List.of(
                worked("2026-03-10", LineSubmission.nonSda("02.01", "Soporte interno", 60))), SUBMISSION);
        assertThat(ok.asText()).contains(";02;02.01;02.01;02.01;02.01;;;Soporte interno;;60");
    }

    @Test
    void proyectoNoSdaEnActividadQueNoLoAdmiteSeRechaza() {
        assertThat(failure(worked("2026-03-10", LineSubmission.nonSda("03.01", "Soporte", 60))).hasCode("NON_SDA_NOT_ALLOWED")).isTrue();
    }

    // ---------------------------------------------------------------- tiempo

    @Test
    void tiempoDebeEstarEntre1Y1440() {
        assertThat(failure(worked("2026-03-10", LineSubmission.of("03.01", 0))).hasCode("TIME_OUT_OF_RANGE")).isTrue();
        assertThat(failure(worked("2026-03-10", LineSubmission.of("03.01", 1441))).hasCode("TIME_OUT_OF_RANGE")).isTrue();

        ActivitiesFile ok = builder.build(List.of(worked("2026-03-10", LineSubmission.of("03.01", 1440))), SUBMISSION);
        assertThat(ok.rowCount()).isEqualTo(1);
    }

    @Test
    void laSumaDelDiaNoPuedeSuperar1440() {
        assertThat(failure(worked("2026-03-10",
                LineSubmission.of("03.01", 1000),
                LineSubmission.sda("01.01.01", "SDATOOL-1", 500)
        )).hasCode("TIME_DAY_EXCEEDED")).isTrue();
    }

    // ---------------------------------------------------------------- duplicados y campos

    @Test
    void lineasRepetidasSeRechazanPorqueBbvaDevuelveApv09() {
        assertThat(failure(worked("2026-03-10",
                LineSubmission.sda("01.01.01", "SDATOOL-1", 100),
                LineSubmission.sda("01.01.01", "SDATOOL-1", 200)
        )).hasCode("DUPLICATE_LINE")).isTrue();
    }

    @Test
    void mismaActividadConDistintoProyectoNoEsDuplicado() {
        ActivitiesFile ok = builder.build(List.of(worked("2026-03-10",
                LineSubmission.sda("01.01.01", "SDATOOL-1", 100),
                LineSubmission.sda("01.01.01", "SDATOOL-2", 200)
        )), SUBMISSION);
        assertThat(ok.rowCount()).isEqualTo(2);
    }

    @Test
    void unCampoConPuntoYComaRomperiaElCsvYSeRechaza() {
        assertThat(failure(worked("2026-03-10", LineSubmission.nonSda("02.01", "Proyecto; raro", 60)))
                .hasCode("FIELD_INVALID")).isTrue();
    }

    // ---------------------------------------------------------------- comportamiento global

    @Test
    void recogeTodosLosProblemasYNoGeneraNada() {
        ActivitiesFileValidationException ex = failure(
                worked("2026-03-10", LineSubmission.of("01.01.01", 60)),   // SDA_REQUIRED
                worked("2026-03-11", LineSubmission.of("99", 60)),          // TREE_NODE_UNKNOWN
                worked("2026-03-25", LineSubmission.of("03.01", 60))        // DATE_FUTURE
        );

        assertThat(ex.getProblems()).hasSize(3);
        assertThat(ex.getProblems()).extracting(ValidationProblem::code)
                .containsExactlyInAnyOrder("SDA_REQUIRED", "TREE_NODE_UNKNOWN", "DATE_FUTURE");
        assertThat(ex.getMessage()).contains("3 problema(s)");
    }

    @Test
    void enModoToleranteSeExcluyenLosDiasInvalidosYSeGeneraElResto() {
        ActivitiesFileBuilder.LenientResult result = builder.buildSkippingInvalid(List.of(
                worked("2026-03-10", LineSubmission.of("03.01", 60)),
                worked("2026-03-11", LineSubmission.of("03.01", 0)),                 // TIME_OUT_OF_RANGE
                DaySubmission.worked("XE2", LocalDate.parse("2026-03-11"), List.of(LineSubmission.of("03.01", 30)))
        ), SUBMISSION);

        assertThat(result.file().dayCount()).isEqualTo(2);
        assertThat(result.file().rowCount()).isEqualTo(2);
        assertThat(result.excludedDays()).isEqualTo(1);
        assertThat(result.problems()).extracting(ValidationProblem::code).containsExactly("TIME_OUT_OF_RANGE");
        assertThat(result.excludedDayKeys()).containsExactly(UID + "|2026-03-11");
        assertThat(result.file().asText()).doesNotContain("2026-03-11;" + PERSON_ID);
        assertThat(result.file().asText()).contains("2026-03-11;" + PersonIdHasher.hash("XE2"));
    }

    @Test
    void unDiaValidoYOtroInvalidoNoGeneraFicheroParcial() {
        assertThatThrownBy(() -> builder.build(List.of(
                worked("2026-03-10", LineSubmission.of("03.01", 60)),
                worked("2026-03-11", LineSubmission.of("03.01", 0))
        ), SUBMISSION)).isInstanceOf(ActivitiesFileValidationException.class);
    }
}
