package com.vendor.arconnector.activities;

import com.vendor.arconnector.activities.CatalogRules.*;
import com.vendor.arconnector.activities.TaskTree.Node;
import com.vendor.arconnector.activities.TaskTree.Requirements;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del documento que dependen de los catálogos: estados y márgenes de
 * SDA, estados de issues de Jira, bajas, evolutivos, conceptualization time y
 * nodos ocultos.
 */
class CatalogRulesTest {

    private static final String UID = "XE1";
    private static final Requirements SDA_ONLY = new Requirements(true, false, false, false);
    private static final Requirements SDA_JIRA = new Requirements(true, true, false, false);
    private static final Requirements NON_SDA = new Requirements(false, false, true, false);
    private static final Requirements EVO_SDA = new Requirements(true, false, false, false, true, false, false);
    private static final Requirements EVO_FEATURE = new Requirements(true, true, false, false, true, true, false);
    private static final Requirements CONCEPT = new Requirements(true, true, false, false, false, false, true);

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private final CatalogRules rules = CatalogRules.of(
            List.of(
                    new SdaInfo("SDA-FLOW", "Flow", null, "Spain", "4Q 2026", d("2026-06-30"), false),
                    new SdaInfo("SDA-EVO", "Flow", null, "Spain", "4Q 2026", d("2026-01-15"), true),
                    new SdaInfo("SDA-ENDED", "Ended", null, "Spain", "2Q 2026", d("2026-06-30"), false),     // margen ES 1Q -> hasta 3Q 2026
                    new SdaInfo("SDA-HOLD", "On hold", null, "Mexico", "1Q 2027", d("2026-08-01"), false),    // margen MX 1Q -> hasta 4Q 2026
                    new SdaInfo("SDA-OFF", "Flow", d("2026-03-31"), "Spain", "4Q 2026", null, false),
                    new SdaInfo("SDA-NOCOUNTRY", "Ended", null, "Atlantis", "1Q 2020", null, false)),
            List.of(
                    new IssueInfo("ETR-NEW", "SDA-FLOW", "New", null, "feature"),
                    new IssueInfo("ETR-PROG", "SDA-FLOW", "In progress", null, "task"),
                    new IssueInfo("ETR-DEPLOYED", "SDA-FLOW", "Deployed", null, "feature"),
                    new IssueInfo("ETR-DEPLOYED-OLD", "SDA-FLOW", "Deployed", null, "feature"),
                    new IssueInfo("ETR-DISCARDED", "SDA-FLOW", "Discarded", null, "feature"),
                    new IssueInfo("ETR-MOVED", "SDA-OTHER", "In progress", null, "feature"),
                    new IssueInfo("ETR-EVO", "SDA-EVO", "In progress", null, "feature"),
                    new IssueInfo("ETR-CT", "SDA-FLOW", "New", null, "conceptualization time"),
                    new IssueInfo("ETR-OFF", "SDA-FLOW", "In progress", d("2026-09-05"), "feature")),
            List.of(
                    new CountryInfo("Spain", 1, 2),
                    new CountryInfo("Mexico", 0, 1)),
            List.of(
                    new StatusChange("ETR-DEPLOYED", "Deployed", d("2026-08-21")),
                    new StatusChange("ETR-DISCARDED", "In progress", d("2026-08-01")),
                    new StatusChange("ETR-DISCARDED", "Discarded", d("2026-09-03"))),
            List.of(new IssueSdaLink("ETR-MOVED", "SDA-FLOW")),
            List.of(
                    new NonSdaInfo("Soporte interno", null),
                    new NonSdaInfo("Proyecto retirado", d("2026-06-30"))),
            List.of(new HiddenNode(PersonIdHasher.hash(UID), d("2026-09-09"), "03")));

    private List<String> codes(List<ValidationProblem> problems) {
        return problems.stream().map(ValidationProblem::code).toList();
    }

    private List<ValidationProblem> check(String date, LineSubmission line, Requirements req, String... path) {
        return rules.validate(UID, d(date), line, List.of(path), req);
    }

    // ---------------------------------------------------------------- SDA

    @Test
    void sdaCorrectoNoDaProblemas() {
        assertThat(check("2026-09-10", LineSubmission.sda("01.01.01", "SDA-FLOW", 480), SDA_ONLY, "01", "01.01", "01.01.01")).isEmpty();
    }

    @Test
    void sdaDesconocidoODeBaja() {
        assertThat(codes(check("2026-09-10", LineSubmission.sda("x", "SDA-NOPE", 60), SDA_ONLY, "01"))).containsExactly("SDA_UNKNOWN");
        assertThat(codes(check("2026-04-01", LineSubmission.sda("x", "SDA-OFF", 60), SDA_ONLY, "01"))).containsExactly("SDA_DISABLED");
        assertThat(check("2026-03-31", LineSubmission.sda("x", "SDA-OFF", 60), SDA_ONLY, "01")).isEmpty();   // hasta ese día incluido
    }

    @Test
    void sdaEndedSeAdmiteDuranteElMargenDeTrimestresDelPais() {
        // Ended en 2Q 2026 + 1 trimestre de margen (Spain) = hasta 3Q 2026
        assertThat(check("2026-09-30", LineSubmission.sda("x", "SDA-ENDED", 60), SDA_ONLY, "01")).isEmpty();
        assertThat(codes(check("2026-10-01", LineSubmission.sda("x", "SDA-ENDED", 60), SDA_ONLY, "01"))).containsExactly("SDA_ENDED");
    }

    @Test
    void sdaOnHoldSeAdmiteDuranteElMargenDesdeElUltimoCambio() {
        // On hold desde 2026-08-01 (3Q) + 1 trimestre (Mexico) = hasta 4Q 2026
        assertThat(check("2026-12-31", LineSubmission.sda("x", "SDA-HOLD", 60), SDA_ONLY, "01")).isEmpty();
        assertThat(codes(check("2027-01-01", LineSubmission.sda("x", "SDA-HOLD", 60), SDA_ONLY, "01"))).containsExactly("SDA_ON_HOLD");
    }

    @Test
    void sinDatosDePaisNoSeAplicaElMargen() {
        assertThat(check("2026-09-10", LineSubmission.sda("x", "SDA-NOCOUNTRY", 60), SDA_ONLY, "01")).isEmpty();
    }

    @Test
    void sdaDeEvolutivoSoloEnActividadDeEvolutivoYViceversa() {
        assertThat(codes(check("2026-09-10", LineSubmission.sda("x", "SDA-EVO", 60), SDA_ONLY, "01"))).containsExactly("SDA_EVOLUTIONARY_MISMATCH");
        assertThat(codes(check("2026-09-10", LineSubmission.sda("x", "SDA-FLOW", 60), EVO_SDA, "01"))).containsExactly("SDA_EVOLUTIONARY_MISMATCH");
        assertThat(check("2026-09-10", LineSubmission.sda("x", "SDA-EVO", 60), EVO_SDA, "01")).isEmpty();
    }

    // ---------------------------------------------------------------- Jira

    @Test
    void issueCorrectaNoDaProblemas() {
        assertThat(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-PROG", 60), SDA_JIRA, "01")).isEmpty();
        assertThat(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-NEW", 60), SDA_JIRA, "01")).isEmpty();
    }

    @Test
    void issueDesconocidaDeBajaODeOtroSda() {
        assertThat(codes(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-NOPE", 60), SDA_JIRA, "01"))).containsExactly("JIRA_UNKNOWN");
        assertThat(codes(check("2026-09-06", LineSubmission.jira("x", "SDA-FLOW", "ETR-OFF", 60), SDA_JIRA, "01"))).containsExactly("JIRA_DISABLED");
        assertThat(codes(check("2026-09-10", LineSubmission.jira("x", "SDA-EVO", "ETR-PROG", 60), EVO_FEATURE, "01"))).containsExactly("JIRA_WRONG_SDA");
    }

    @Test
    void elHistoricoDeFeaturesYSdaTambienValidaLaPertenencia() {
        // ETR-MOVED está en el catálogo con SDA-OTHER, pero el histórico la vincula a SDA-FLOW
        assertThat(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-MOVED", 60), SDA_JIRA, "01")).isEmpty();
    }

    @Test
    void issueDeployedSoloHasta30DiasDespuesDelCambio() {
        assertThat(check("2026-09-20", LineSubmission.jira("x", "SDA-FLOW", "ETR-DEPLOYED", 60), SDA_JIRA, "01")).isEmpty();
        assertThat(codes(check("2026-09-21", LineSubmission.jira("x", "SDA-FLOW", "ETR-DEPLOYED", 60), SDA_JIRA, "01"))).containsExactly("JIRA_STATUS");
        // Deployed sin fecha en el histórico (el histórico solo trae 30 días): hace más de 30 días
        assertThat(codes(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-DEPLOYED-OLD", 60), SDA_JIRA, "01"))).containsExactly("JIRA_STATUS");
    }

    @Test
    void issueDiscardedSoloHastaElDiaDelCambio() {
        assertThat(check("2026-09-03", LineSubmission.jira("x", "SDA-FLOW", "ETR-DISCARDED", 60), SDA_JIRA, "01")).isEmpty();
        assertThat(codes(check("2026-09-04", LineSubmission.jira("x", "SDA-FLOW", "ETR-DISCARDED", 60), SDA_JIRA, "01"))).containsExactly("JIRA_STATUS");
    }

    @Test
    void conceptualizationTimeSoloEnSuActividadYViceversa() {
        assertThat(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-CT", 60), CONCEPT, "01")).isEmpty();
        assertThat(codes(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-CT", 60), SDA_JIRA, "01"))).containsExactly("JIRA_TYPE_MISMATCH");
        assertThat(codes(check("2026-09-10", LineSubmission.jira("x", "SDA-FLOW", "ETR-PROG", 60), CONCEPT, "01"))).containsExactly("JIRA_TYPE_MISMATCH");
    }

    @Test
    void featureDeEvolutivoSoloEnActividadDeEvolutivo() {
        assertThat(check("2026-09-10", LineSubmission.jira("x", "SDA-EVO", "ETR-EVO", 60), EVO_FEATURE, "01")).isEmpty();
        List<String> codes = codes(check("2026-09-10", LineSubmission.jira("x", "SDA-EVO", "ETR-EVO", 60), SDA_JIRA, "01"));
        assertThat(codes).contains("SDA_EVOLUTIONARY_MISMATCH", "JIRA_EVOLUTIONARY_MISMATCH");
    }

    // ---------------------------------------------------------------- no SDA y nodos ocultos

    @Test
    void proyectoNoSdaDebeExistirYEstarVigente() {
        assertThat(check("2026-09-10", LineSubmission.nonSda("x", "Soporte interno", 60), NON_SDA, "02")).isEmpty();
        assertThat(codes(check("2026-09-10", LineSubmission.nonSda("x", "Otra cosa", 60), NON_SDA, "02"))).containsExactly("NON_SDA_UNKNOWN");
        assertThat(codes(check("2026-07-01", LineSubmission.nonSda("x", "Proyecto retirado", 60), NON_SDA, "02"))).containsExactly("NON_SDA_DISABLED");
    }

    @Test
    void nodoOcultoParaLaPersonaEseDiaIncluyendoDescendientes() {
        Requirements none = new Requirements(false, false, false, false);
        assertThat(codes(check("2026-09-09", LineSubmission.of("03.01", 60), none, "03", "03.01"))).containsExactly("TASK_TYPE_HIDDEN");
        assertThat(check("2026-09-10", LineSubmission.of("03.01", 60), none, "03", "03.01")).isEmpty();
        assertThat(rules.validate("XE2", d("2026-09-09"), LineSubmission.of("03.01", 60), List.of("03", "03.01"), none)).isEmpty();
    }

    @Test
    void desactivadasNoAplicanNada() {
        assertThat(CatalogRules.disabled().validate(UID, d("2026-09-10"), LineSubmission.sda("x", "SDA-NOPE", 60), List.of("01"), SDA_ONLY)).isEmpty();
    }

    @Test
    void parseoDeTrimestres() {
        assertThat(CatalogRules.parseQuarter("1Q 2022")).isEqualTo(2022 * 4);
        assertThat(CatalogRules.parseQuarter("4Q2026")).isEqualTo(2026 * 4 + 3);
        assertThat(CatalogRules.parseQuarter("Q3 2025")).isEqualTo(2025 * 4 + 2);
        assertThat(CatalogRules.parseQuarter("2025 Q2")).isEqualTo(2025 * 4 + 1);
        assertThat(CatalogRules.parseQuarter("cuando salga")).isNull();
        assertThat(CatalogRules.quarterIndex(d("2026-09-10"))).isEqualTo(2026 * 4 + 2);
    }

    // ---------------------------------------------------------------- integración con el generador

    @Test
    void elGeneradorDelFicheroAplicaLasReglasDeCatalogo() {
        TaskTree tree = TaskTree.of(List.of(
                new Node("01", null, false, null, true, false, false, false),
                Node.leaf("01.01", "01")));
        ActivitiesFileBuilder builder = new ActivitiesFileBuilder(tree, new ModifiablePeriodPolicy(), rules);

        ActivitiesFileBuilder.LenientResult result = builder.buildSkippingInvalid(List.of(
                DaySubmission.worked(UID, d("2026-09-09"), List.of(LineSubmission.sda("01.01", "SDA-FLOW", 480))),
                DaySubmission.worked(UID, d("2026-09-10"), List.of(LineSubmission.sda("01.01", "SDA-OFF", 480)))
        ), d("2026-09-10"));

        assertThat(result.file().dayCount()).isEqualTo(1);
        assertThat(codes(result.problems())).containsExactly("SDA_DISABLED");
    }
}
