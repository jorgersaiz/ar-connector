package com.vendor.arconnector.catalog;

import com.vendor.arconnector.activities.PersonIdHasher;
import com.vendor.arconnector.activities.TaskTree;
import com.vendor.arconnector.domain.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lectura de cada catálogo con los ficheros de muestra de samples/catalogues,
 * que siguen las columnas del documento v1.16.
 */
class CatalogParsersTest {

    private static CsvTable sample(CatalogName name) {
        String path = "samples/catalogues/" + name.apiName() + ".csv";
        try (InputStream in = CatalogParsersTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No se encuentra la muestra " + path);
            }
            return SemicolonCsv.parse(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void arbolDeActividades() {
        List<TaskType> types = CatalogParsers.taskTypes(sample(CatalogName.TASK_TYPES));

        assertThat(types).hasSize(13);

        TaskType root = types.stream().filter(t -> t.getTaskTypeId().equals("01")).findFirst().orElseThrow();
        assertThat(root.getTaskTypeParentId()).isNull();
        assertThat(root.isRequiresSdaProject()).isTrue();
        assertThat(root.isCanBeSelected()).isFalse();
        assertThat(root.getSortOrder()).isEqualTo(1);

        TaskType build = types.stream().filter(t -> t.getTaskTypeId().equals("01.01.01")).findFirst().orElseThrow();
        assertThat(build.getTaskTypeParentId()).isEqualTo("01.01");
        assertThat(build.getHelpEs()).isEqualTo("Codificación, pruebas unitarias; revisión de código");
        assertThat(build.isCanBeSelected()).isTrue();

        TaskType legacy = types.stream().filter(t -> t.getTaskTypeId().equals("03.03")).findFirst().orElseThrow();
        assertThat(legacy.getDisabledDate()).isEqualTo(LocalDate.parse("2026-03-10"));

        TaskType concept = types.stream().filter(t -> t.getTaskTypeId().equals("01.03")).findFirst().orElseThrow();
        assertThat(concept.isRequiresConceptualizationTime()).isTrue();
        assertThat(concept.isRequiresJiraIssue()).isTrue();

        // El catálogo cargado alimenta directamente el árbol del generador de ficheros
        TaskTree tree = TaskTree.fromCatalog(types);
        assertThat(tree.fiveLevels("01.01.01")).containsExactly("01", "01.01", "01.01.01", "01.01.01", "01.01.01");
        assertThat(tree.effectiveRequirementsFor("01.01.02").sdaProject()).isTrue();
        assertThat(tree.effectiveRequirementsFor("01.01.02").jiraIssue()).isTrue();
    }

    @Test
    void proyectosSda() {
        List<SdaProject> projects = CatalogParsers.sdaProjects(sample(CatalogName.SDA_PROJECTS));

        assertThat(projects).hasSize(5);
        SdaProject evo = projects.stream().filter(p -> p.getSdaProjectId().equals("SDATOOL-10001")).findFirst().orElseThrow();
        assertThat(evo.isEvolutionarySda()).isTrue();
        assertThat(evo.getStartQuarter()).isEqualTo("1Q2026");
        assertThat(evo.getStatus()).isEqualTo("Stock");
        assertThat(evo.getLastModifiedStatusDate()).isEqualTo(LocalDate.parse("2026-01-15"));

        SdaProject closed = projects.stream().filter(p -> p.getSdaProjectId().equals("SDATOOL-40123")).findFirst().orElseThrow();
        assertThat(closed.getDisabledDate()).isEqualTo(LocalDate.parse("2025-03-31"));
        assertThat(closed.getCountry()).isEqualTo("PORTUGAL");
    }

    @Test
    void issuesDeJira() {
        List<JiraIssue> issues = CatalogParsers.jiraIssues(sample(CatalogName.JIRA_ISSUES));

        assertThat(issues).hasSize(7);
        JiraIssue ct = issues.stream().filter(i -> i.getJiraIssueId().equals("ETR-04")).findFirst().orElseThrow();
        assertThat(ct.getType()).isEqualTo("Conceptualization Time");
        assertThat(ct.getGeography()).isEqualTo("Mexico");
        assertThat(ct.getSdaProjectId()).isEqualTo("SDATOOL-30777");
        assertThat(ct.getStatus()).isEqualTo("New");

        // como en el catálogo real: la misma issue en dos SDA, y una issue sin SDA
        assertThat(issues).filteredOn(i -> i.getJiraIssueId().equals("ETR-01"))
                .extracting(JiraIssue::getSdaProjectId).containsExactly("SDATOOL-37040", "SDATOOL-20500");
        assertThat(issues).filteredOn(i -> i.getJiraIssueId().equals("ETR-06"))
                .extracting(JiraIssue::getSdaProjectId).containsExactly((String) null);
        assertThat(issues).extracting(JiraIssue::getGeography).contains("Global - Retail Client Solutions");
    }

    @Test
    void unaFilaDeIssueRepetidaExactamenteSeIgnoraEnVezDeTumbarElCatalogo() {
        List<JiraIssue> issues = CatalogParsers.jiraIssues(SemicolonCsv.parse(
                "jira_issue_id;description;sda_project_id;status;geography;disabled_date;type\n"
                        + "A-1;x;SDA-1;New;Spain;;Feature\nA-1;x;SDA-1;New;Spain;;Feature\nA-1;x;SDA-2;New;Spain;;Feature\n"));

        assertThat(issues).extracting(JiraIssue::getSdaProjectId).containsExactly("SDA-1", "SDA-2");
    }

    @Test
    void unasComillasAMitadDeUnTextoSonUnCaracterMas() {
        List<NonSdaProject> projects = CatalogParsers.nonSdaProjects(SemicolonCsv.parse(
                "description;disabled_date\nPantallas de 24\" para oficinas;\nOtro proyecto;\n"));

        assertThat(projects).extracting(NonSdaProject::getDescription)
                .containsExactly("Pantallas de 24\" para oficinas", "Otro proyecto");
    }

    @Test
    void historicos() {
        List<JiraIssueStatusHistory> statuses = CatalogParsers.jiraIssuesHistorical(sample(CatalogName.JIRA_ISSUES_HISTORICAL));
        assertThat(statuses).hasSize(3);
        assertThat(statuses.get(2).getJiraIssueKey()).isEqualTo("ETR-05");
        assertThat(statuses.get(2).getStatus()).isEqualTo("Deployed");
        assertThat(statuses.get(2).getStatusChangeDate()).isEqualTo(LocalDate.parse("2026-08-21"));

        // una feature con dos SDA aparece en dos filas con la misma clave: no es un duplicado
        List<JiraIssueSdaProjectHistory> links = CatalogParsers.jiraIssueSdaProjectsHistorical(sample(CatalogName.JIRA_ISSUE_SDA_PROJECTS_HISTORICAL));
        // y con el SDA vacío la feature se quedó sin proyecto ese día
        assertThat(links).hasSize(3);
        assertThat(links).extracting(JiraIssueSdaProjectHistory::getSdaProjectId).containsExactly("SDATOOL-37040", "SDATOOL-20500", null);
    }

    @Test
    void proyectosNoSdaYPaises() {
        List<NonSdaProject> nonSda = CatalogParsers.nonSdaProjects(sample(CatalogName.NON_SDA_PROJECTS));
        assertThat(nonSda).extracting(NonSdaProject::getDescription)
                .containsExactly("Soporte interno", "Oficina de proyectos", "Proyecto retirado");
        assertThat(nonSda.get(2).getDisabledDate()).isEqualTo(LocalDate.parse("2026-06-30"));

        List<Country> countries = CatalogParsers.countries(sample(CatalogName.COUNTRIES));
        assertThat(countries).hasSize(3);
        Country es = countries.get(0);
        assertThat(es.getCountryCode()).isEqualTo("ESP");
        assertThat(es.getEndedMargin()).isEqualTo(1);
        assertThat(es.getOnholdMargin()).isEqualTo(2);
        assertThat(es.getSdaCountry()).isEqualTo("SPAIN");
        assertThat(es.getTimezone()).isEqualTo("Europe/Madrid");
    }

    @Test
    void nodosOcultosConPersonIdEnElFormatoDeNuestroHasher() {
        List<HiddenTaskType> hidden = CatalogParsers.hiddenTaskTypes(sample(CatalogName.HIDDEN_TASK_TYPES));

        assertThat(hidden).hasSize(2);
        // Activity Report escribe el hash en mayúsculas; nosotros lo enviamos en minúsculas y lo acepta
        assertThat(hidden.get(0).getPersonId()).isEqualTo(PersonIdHasher.hash("xe12345").toUpperCase());
        assertThat(hidden.get(0).getGeography()).isEqualTo("HOLDING ARQ");
        assertThat(hidden.get(0).getDate()).isEqualTo(LocalDate.parse("2026-09-03"));
        assertThat(hidden.get(0).getTaskTypeCode()).isEqualTo("03.02");
    }

    @Test
    void unPaisSinMargenSeAceptaConElMargenVacio() {
        // visto en el catálogo countries de DEV (noviembre 2026)
        CsvTable table = SemicolonCsv.parse("country_code;ended_margin;onhold_margin;sda_country\nESP;1;2;SPAIN\nMEX;1;;MEXICO\n");

        List<Country> countries = CatalogParsers.countries(table);

        assertThat(countries).hasSize(2);
        assertThat(countries.get(1).getEndedMargin()).isEqualTo(1);
        assertThat(countries.get(1).getOnholdMargin()).isNull();
    }

    @Test
    void identificadorRepetidoDescartaElCatalogoEntero() {
        CsvTable table = SemicolonCsv.parse("country_code;ended_margin;onhold_margin\nESP;1;1\nESP;2;2\n");

        assertThatThrownBy(() -> CatalogParsers.countries(table))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("repetido")
                .hasMessageContaining("línea 3");
    }

    @Test
    void columnaObligatoriaAusenteDescartaElCatalogo() {
        CsvTable table = SemicolonCsv.parse("sda_project_id;description\nSDATOOL-1;x\n");

        assertThatThrownBy(() -> CatalogParsers.sdaProjects(table))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("faltan columnas");
    }

    @Test
    void seAcumulanLosErroresDeVariasFilas() {
        CsvTable table = SemicolonCsv.parse(
                "jira_issue_key;status;status_change_date\nA;New;2026-01-01\nB;New;ayer\n;New;2026-01-02\n");

        assertThatThrownBy(() -> CatalogParsers.jiraIssuesHistorical(table))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("2 error(es)")
                .hasMessageContaining("línea 3")
                .hasMessageContaining("línea 4");
    }
}
