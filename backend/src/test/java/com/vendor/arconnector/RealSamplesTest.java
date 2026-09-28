package com.vendor.arconnector;

import com.vendor.arconnector.activities.*;
import com.vendor.arconnector.catalog.CatalogParsers;
import com.vendor.arconnector.catalog.CsvRow;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.domain.FeedbackRow;
import com.vendor.arconnector.feedback.ActivityRowKeys;
import com.vendor.arconnector.feedback.FeedbackLocator;
import com.vendor.arconnector.feedback.FeedbackParser;
import com.vendor.arconnector.registration.UserRegistrationFeedbackParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contraste con los ficheros REALES que facilita BBVA ("API VENDORS - Ejemplo
 * de ficheros devueltos y recibidos"): todos los catálogos se leen, y las
 * reglas del conector aplicadas a un envío real coinciden con el veredicto de
 * Activity Report.
 *
 * Los ficheros son datos de BBVA y no se versionan. Se buscan en el directorio
 * indicado por la variable AR_REAL_SAMPLES_DIR o en samples/real; si no están,
 * el test se omite.
 */
@EnabledIf("samplesAvailable")
class RealSamplesTest {

    private static Path dir;

    static boolean samplesAvailable() {
        return locate().isPresent();
    }

    private static Optional<Path> locate() {
        List<Path> candidates = new ArrayList<>();
        String env = System.getenv("AR_REAL_SAMPLES_DIR");
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env));
        }
        candidates.add(Path.of("..", "samples", "real"));
        candidates.add(Path.of("samples", "real"));
        return candidates.stream().filter(Files::isDirectory).filter(p -> find(p, "task_types_2").isPresent()).findFirst();
    }

    private static Optional<Path> find(Path directory, String fragment) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(f -> f.getFileName().toString().contains(fragment)).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static CsvTable table(String fragment) throws IOException {
        Path file = find(dir, fragment).orElseThrow(() -> new IllegalStateException("Falta el fichero " + fragment));
        return SemicolonCsv.parse(Files.readAllBytes(file));
    }

    private static byte[] bytes(String fragment) throws IOException {
        return Files.readAllBytes(find(dir, fragment).orElseThrow());
    }

    @BeforeAll
    static void setUp() {
        dir = locate().orElseThrow();
    }

    @Test
    void todosLosCatalogosRealesSeLeen() throws IOException {
        assertThat(CatalogParsers.taskTypes(table("task_types_2"))).hasSizeGreaterThan(100);
        assertThat(CatalogParsers.sdaProjects(table("sda_projects_2"))).hasSizeGreaterThan(1000);
        assertThat(CatalogParsers.jiraIssues(table("jira_issues_2"))).hasSizeGreaterThan(100_000);
        assertThat(CatalogParsers.jiraIssuesHistorical(table("jira_issues_historical_"))).hasSizeGreaterThan(10_000);
        assertThat(CatalogParsers.jiraIssueSdaProjectsHistorical(table("jira_issue_sda_projects_historical_"))).hasSizeGreaterThan(1000);
        assertThat(CatalogParsers.nonSdaProjects(table("non_sda_projects_"))).hasSizeGreaterThan(100);
        assertThat(CatalogParsers.countries(table("countries_"))).hasSizeGreaterThan(5);
        assertThat(CatalogParsers.hiddenTaskTypes(table("hidden_task_types_"))).isNotEmpty();
    }

    @Test
    void elFeedbackRealCorrespondeAlEnvioReal() throws IOException {
        Set<String> sent = ActivityRowKeys.of(bytes("vendors-file-reception"));
        Set<String> feedback = ActivityRowKeys.of(bytes("activities_"));

        assertThat(feedback).isEqualTo(sent);
        assertThat(FeedbackParser.parse(table("activities_"))).isNotEmpty();
    }

    @Test
    void elFeedbackDeRegistroRealTraeElHashDeCadaUidEnviado() throws IOException {
        Set<String> sentHashes = table("vendors_user_registration").rows().stream()
                .map(r -> PersonIdHasher.hash(r.text("uids")).toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> returned = UserRegistrationFeedbackParser.parse(table("users_")).stream()
                .map(UserRegistrationFeedbackParser.Row::uid).collect(Collectors.toSet());

        assertThat(returned).isEqualTo(sentHashes);
    }

    /**
     * Las reglas del conector, aplicadas a cada línea del envío real, frente a
     * lo que decidió Activity Report. No se comparan los rechazos que dependen
     * de la persona o de su calendario (APV_01, APV_02, APV_10), porque el
     * envío no dice quién es cada hash.
     */
    @Test
    void lasReglasCoincidenConElVeredictoDeActivityReport() throws IOException {
        TaskTree tree = TaskTree.fromCatalog(CatalogParsers.taskTypes(table("task_types_2")));
        CatalogRules rules = CatalogRules.fromCatalog(
                CatalogParsers.sdaProjects(table("sda_projects_2")),
                CatalogParsers.jiraIssues(table("jira_issues_2")),
                CatalogParsers.countries(table("countries_")),
                CatalogParsers.jiraIssuesHistorical(table("jira_issues_historical_")),
                CatalogParsers.jiraIssueSdaProjectsHistorical(table("jira_issue_sda_projects_historical_")),
                CatalogParsers.nonSdaProjects(table("non_sda_projects_")),
                List.of());
        ActivitiesFileBuilder builder = new ActivitiesFileBuilder(tree, new ModifiablePeriodPolicy(), rules);

        List<String> blockedButAccepted = new ArrayList<>();
        List<String> sentButRejected = new ArrayList<>();
        int compared = 0;
        for (FeedbackRow row : FeedbackParser.parse(table("activities_"))) {
            String reason = row.getReason() == null ? "" : row.getReason();
            if (row.isNonWorkingDay() || Set.of("APV_01", "APV_02", "APV_10").contains(reason)) {
                continue;
            }
            // Cada línea se valida sola: el mínimo de 1 minuto lo cubre TIME_OUT_OF_RANGE
            LineSubmission line = new LineSubmission(row.leafTaskTypeCode(), row.getSdaProjectId(), row.getJiraIssueId(),
                    row.getNonSdaProject(), row.getTimeMinutes() == null ? 0 : row.getTimeMinutes());
            List<ValidationProblem> problems = builder.validateContent(
                    DaySubmission.worked("XE00000", row.getActivityDate(), List.of(line)));
            compared++;
            String description = row.getActivityDate() + " " + row.leafTaskTypeCode() + " sda=" + row.getSdaProjectId()
                    + " jira=" + row.getJiraIssueId() + " t=" + row.getTimeMinutes();
            if (!problems.isEmpty() && reason.isEmpty()) {
                blockedButAccepted.add(description + " -> " + problems.stream().map(ValidationProblem::code).toList());
            }
            if (problems.isEmpty() && !reason.isEmpty()) {
                sentButRejected.add(description + " -> " + reason);
            }
        }

        assertThat(compared).isGreaterThan(5000);
        assertThat(blockedButAccepted).as("líneas que el conector bloquearía y Activity Report aceptó").isEmpty();
        assertThat(sentButRejected).as("líneas que el conector enviaría y Activity Report rechazó").isEmpty();
    }

    @Test
    void elFeedbackSeLocalizaAunqueSePubliqueConLaFechaDelDiaSiguiente() throws IOException {
        byte[] sent = bytes("vendors-file-reception");
        byte[] feedback = bytes("activities_");
        LocalDate submission = LocalDate.parse("2026-09-24");

        Optional<FeedbackLocator.Found> found = FeedbackLocator.locate(submission, LocalDate.parse("2026-09-25"),
                date -> date.equals(LocalDate.parse("2026-09-25")) ? Optional.of(feedback) : Optional.empty(),
                ActivityRowKeys::of, ActivityRowKeys.of(sent));

        assertThat(found).isPresent();
        assertThat(found.get().requestedDate()).isEqualTo(LocalDate.parse("2026-09-25"));
    }
}
