package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.*;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas del documento v1.16 que dependen de los catálogos cedidos por
 * Activity Report. Se aplican a cada línea antes de enviar (y al guardar un
 * día o una plantilla) para no provocar rechazos APV_04 / APV_05 / APV_06 /
 * APV_10 al día siguiente. Lógica pura, sin Spring.
 *
 * Contrastadas con un envío real y su feedback (septiembre 2026, ~9.500
 * líneas): ningún falso positivo ni falso negativo en las reglas comparables.
 *
 * <pre>
 *  Proyectos SDA (APV_04, APV_04_EV)
 *   SDA_UNKNOWN                 el proyecto no está en el catálogo
 *   SDA_DISABLED                disabled_date anterior a la fecha de actividad
 *   SDA_ENDED                   estado Ended y la fecha supera el Q de end_date + ended_margin del país
 *   SDA_ON_HOLD                 estado On hold y la fecha supera el Q de last_modified_status_date + onhold_margin
 *   SDA_EVOLUTIONARY_MISMATCH   SDA de evolutivo en actividad no evolutiva o viceversa
 *  Issues de Jira (APV_05, APV_05_EV, APV_05_CT), SOLO si la actividad espera issue
 *   JIRA_UNKNOWN                la issue no está en el catálogo
 *   JIRA_WRONG_SDA              la issue no estaba asociada a ese SDA en la fecha de actividad
 *   JIRA_DISABLED               disabled_date anterior a la fecha de actividad
 *   JIRA_STATUS                 Discarded: solo hasta el día del cambio; Deployed: hasta 30 días después
 *   JIRA_TYPE_MISMATCH          issue de conceptualization time en actividad que no lo es, o viceversa
 *   JIRA_EVOLUTIONARY_MISMATCH  feature de un SDA evolutivo en actividad no evolutiva, o viceversa
 *  Proyectos no SDA (APV_06)
 *   NON_SDA_UNKNOWN / NON_SDA_DISABLED
 *  Nodos ocultos (APV_10)
 *   TASK_TYPE_HIDDEN            la persona no puede imputar a ese nodo (o a un antecesor) ese día
 * </pre>
 *
 * Particularidades observadas en los datos reales:
 *  - Si la actividad NO espera issue de Jira, Activity Report no valida la
 *    columna jira_issue_id (acepta issues desconocidas o de otro SDA), así que
 *    aquí tampoco se valida.
 *  - Una issue puede estar asociada a varios SDA (varias filas en el catálogo)
 *    o a ninguno ("Features de evolutivo" se imputan con issue y sin SDA).
 *  - La asociación issue-SDA se evalúa en la FECHA DE ACTIVIDAD: si la feature
 *    cambió de SDA el día 22, los días anteriores se validan contra el SDA
 *    anterior, que el catálogo ya no informa; esos días no se pueden comprobar.
 *  - Los hashes de persona de BBVA vienen en mayúsculas.
 *
 * Con {@link #disabled()} no se aplica ninguna regla (tests unitarios del formato).
 */
public final class CatalogRules {

    public static final int DEPLOYED_GRACE_DAYS = 30;
    public static final String STATUS_ENDED = "ended";
    public static final String STATUS_ON_HOLD = "on hold";
    public static final String STATUS_DISCARDED = "discarded";
    public static final String STATUS_DEPLOYED = "deployed";
    public static final String TYPE_CONCEPTUALIZATION_TIME = "conceptualization time";

    /** "1Q 2022", "1Q2022", "Q1 2022", "2022 Q1"... */
    private static final Pattern QUARTER = Pattern.compile("(?:([1-4])\\s*Q\\s*(\\d{4}))|(?:Q\\s*([1-4])\\s*(\\d{4}))|(?:(\\d{4})\\s*Q\\s*([1-4]))", Pattern.CASE_INSENSITIVE);

    public record SdaInfo(String id, String status, LocalDate disabledDate, String country, String endQuarter,
                          LocalDate lastModifiedStatusDate, boolean evolutionary) {
    }

    /** Una fila del catálogo de issues: la misma issue se repite por cada SDA asociado. */
    public record IssueInfo(String id, String sdaProjectId, String status, LocalDate disabledDate, String type) {
    }

    public record CountryInfo(String sdaCountry, int endedMargin, int onholdMargin) {
    }

    public record StatusChange(String issueKey, String status, LocalDate date) {
    }

    /** Cambio de asociación issue-SDA; sdaProjectId null = la issue se quedó sin SDA ese día. */
    public record IssueSdaLink(String issueKey, String sdaProjectId, LocalDate date) {
    }

    public record NonSdaInfo(String description, LocalDate disabledDate) {
    }

    public record HiddenNode(String personId, LocalDate date, String taskTypeCode) {
    }

    private final boolean enabled;
    private final Map<String, SdaInfo> sdas = new HashMap<>();
    private final Map<String, List<IssueInfo>> issues = new HashMap<>();
    private final Map<String, CountryInfo> countriesBySdaCountry = new HashMap<>();
    private final Map<String, List<StatusChange>> statusChangesByIssue = new HashMap<>();
    private final Map<String, List<IssueSdaLink>> linksByIssue = new HashMap<>();
    private final Map<String, NonSdaInfo> nonSdas = new HashMap<>();
    private final Set<String> hidden = new HashSet<>();

    private CatalogRules(boolean enabled) {
        this.enabled = enabled;
    }

    /** Sin reglas de catálogo (solo formato y árbol). */
    public static CatalogRules disabled() {
        return new CatalogRules(false);
    }

    public static CatalogRules of(Collection<SdaInfo> sdas, Collection<IssueInfo> issues, Collection<CountryInfo> countries,
                                  Collection<StatusChange> statusChanges, Collection<IssueSdaLink> links,
                                  Collection<NonSdaInfo> nonSdas, Collection<HiddenNode> hiddenNodes) {
        CatalogRules r = new CatalogRules(true);
        sdas.forEach(s -> r.sdas.put(s.id(), s));
        issues.forEach(i -> r.issues.computeIfAbsent(i.id(), k -> new ArrayList<>()).add(i));
        countries.forEach(c -> {
            if (c.sdaCountry() != null) {
                r.countriesBySdaCountry.put(c.sdaCountry().trim().toLowerCase(Locale.ROOT), c);
            }
        });
        statusChanges.forEach(s -> r.statusChangesByIssue.computeIfAbsent(s.issueKey(), k -> new ArrayList<>()).add(s));
        links.forEach(l -> r.linksByIssue.computeIfAbsent(l.issueKey(), k -> new ArrayList<>()).add(l));
        nonSdas.forEach(n -> r.nonSdas.put(n.description(), n));
        hiddenNodes.forEach(h -> r.hidden.add(hiddenKey(h.personId(), h.date(), h.taskTypeCode())));
        return r;
    }

    public static CatalogRules fromCatalog(Collection<SdaProject> sdas, Collection<JiraIssue> issues, Collection<Country> countries,
                                           Collection<JiraIssueStatusHistory> statusHistory,
                                           Collection<JiraIssueSdaProjectHistory> sdaHistory,
                                           Collection<NonSdaProject> nonSdas, Collection<HiddenTaskType> hiddenNodes) {
        return of(
                sdas.stream().map(s -> new SdaInfo(s.getSdaProjectId(), s.getStatus(), s.getDisabledDate(), s.getCountry(),
                        s.getEndQuarter(), s.getLastModifiedStatusDate(), s.isEvolutionarySda())).toList(),
                issues.stream().map(i -> new IssueInfo(i.getJiraIssueId(), i.getSdaProjectId(), i.getStatus(), i.getDisabledDate(), i.getType())).toList(),
                countries.stream().map(c -> new CountryInfo(c.getSdaCountry(), c.getEndedMargin(), c.getOnholdMargin())).toList(),
                statusHistory.stream().map(h -> new StatusChange(h.getJiraIssueKey(), h.getStatus(), h.getStatusChangeDate())).toList(),
                sdaHistory.stream().map(h -> new IssueSdaLink(h.getJiraIssueKey(), h.getSdaProjectId(), h.getChangeDate())).toList(),
                nonSdas.stream().map(n -> new NonSdaInfo(n.getDescription(), n.getDisabledDate())).toList(),
                hiddenNodes.stream().map(h -> new HiddenNode(h.getPersonId(), h.getDate(), h.getTaskTypeCode())).toList());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** true si en esa actividad Activity Report espera (y por tanto valida) una issue de Jira. */
    public static boolean expectsJiraIssue(TaskTree.Requirements req) {
        return req.jiraIssue() || req.conceptualizationTime() || req.evolutionaryJiraIssue();
    }

    /**
     * Problemas de catálogo de una línea ya validada en formato y árbol.
     *
     * @param path camino raíz→hoja del nodo imputado
     * @param req  exigencias efectivas del nodo (heredadas del camino)
     */
    public List<ValidationProblem> validate(String uid, LocalDate date, LineSubmission line, List<String> path,
                                            TaskTree.Requirements req) {
        List<ValidationProblem> problems = new ArrayList<>();
        if (!enabled) {
            return problems;
        }

        String personId = PersonIdHasher.hash(uid);
        for (String node : path) {
            if (hidden.contains(hiddenKey(personId, date, node))) {
                problems.add(problem(uid, date, "TASK_TYPE_HIDDEN",
                        "Activity Report no permite a esta persona imputar a " + node + " el " + date));
                break;
            }
        }

        SdaInfo sda = null;
        if (notBlank(line.sdaProjectId())) {
            sda = sdas.get(line.sdaProjectId().trim());
            if (sda == null) {
                problems.add(problem(uid, date, "SDA_UNKNOWN", "el proyecto SDA " + line.sdaProjectId() + " no está en el catálogo"));
            } else {
                validateSda(uid, date, sda, req, problems);
            }
        }

        if (notBlank(line.jiraIssueId()) && expectsJiraIssue(req)) {
            List<IssueInfo> rows = issues.get(line.jiraIssueId().trim());
            if (rows == null || rows.isEmpty()) {
                problems.add(problem(uid, date, "JIRA_UNKNOWN", "la issue " + line.jiraIssueId() + " no está en el catálogo"));
            } else {
                validateIssue(uid, date, rows, line.sdaProjectId(), sda, req, problems);
            }
        }

        if (notBlank(line.nonSdaProject())) {
            NonSdaInfo nonSda = nonSdas.get(line.nonSdaProject().trim());
            if (nonSda == null) {
                problems.add(problem(uid, date, "NON_SDA_UNKNOWN", "el proyecto no SDA '" + line.nonSdaProject() + "' no está en el catálogo"));
            } else if (nonSda.disabledDate() != null && date.isAfter(nonSda.disabledDate())) {
                problems.add(problem(uid, date, "NON_SDA_DISABLED", "el proyecto no SDA '" + nonSda.description() + "' está de baja desde "
                        + nonSda.disabledDate().plusDays(1)));
            }
        }
        return problems;
    }

    private void validateSda(String uid, LocalDate date, SdaInfo sda, TaskTree.Requirements req, List<ValidationProblem> problems) {
        if (sda.disabledDate() != null && date.isAfter(sda.disabledDate())) {
            problems.add(problem(uid, date, "SDA_DISABLED", "el proyecto SDA " + sda.id() + " está de baja desde " + sda.disabledDate().plusDays(1)));
        }

        String status = sda.status() == null ? "" : sda.status().trim().toLowerCase(Locale.ROOT);
        CountryInfo country = sda.country() == null ? null : countriesBySdaCountry.get(sda.country().trim().toLowerCase(Locale.ROOT));
        int dateQuarter = quarterIndex(date);

        if (status.equals(STATUS_ENDED)) {
            Integer endQuarter = parseQuarter(sda.endQuarter());
            if (endQuarter != null && country != null && dateQuarter > endQuarter + country.endedMargin()) {
                problems.add(problem(uid, date, "SDA_ENDED", "el proyecto SDA " + sda.id() + " está Ended (" + sda.endQuarter()
                        + ") y el margen de " + country.endedMargin() + " trimestre(s) de " + sda.country() + " ya pasó"));
            }
        } else if (status.equals(STATUS_ON_HOLD)) {
            if (sda.lastModifiedStatusDate() != null && country != null
                    && dateQuarter > quarterIndex(sda.lastModifiedStatusDate()) + country.onholdMargin()) {
                problems.add(problem(uid, date, "SDA_ON_HOLD", "el proyecto SDA " + sda.id() + " está On hold desde "
                        + sda.lastModifiedStatusDate() + " y el margen de " + country.onholdMargin() + " trimestre(s) de " + sda.country() + " ya pasó"));
            }
        }

        if (req.evolutionarySda() && !sda.evolutionary()) {
            problems.add(problem(uid, date, "SDA_EVOLUTIONARY_MISMATCH", "la actividad es de evolutivo pero el SDA " + sda.id() + " no es evolutivo"));
        }
        if (!req.evolutionarySda() && sda.evolutionary()) {
            problems.add(problem(uid, date, "SDA_EVOLUTIONARY_MISMATCH", "el SDA " + sda.id() + " es de evolutivo pero la actividad no lo es"));
        }
    }

    private void validateIssue(String uid, LocalDate date, List<IssueInfo> rows, String lineSda, SdaInfo sda,
                               TaskTree.Requirements req, List<ValidationProblem> problems) {
        IssueInfo issue = rows.get(0);   // estado, tipo y baja son de la issue, iguales en todas sus filas
        String wantedSda = lineSda == null ? "" : lineSda.trim();

        if (!wantedSda.isEmpty()) {
            Optional<Set<String>> associated = sdasAssociatedOn(issue.id(), rows, date);
            if (associated.isPresent() && !associated.get().contains(wantedSda)) {
                problems.add(problem(uid, date, "JIRA_WRONG_SDA", "la issue " + issue.id() + " no estaba asociada al SDA " + wantedSda
                        + " el " + date + " (asociada a: " + (associated.get().isEmpty() ? "ninguno" : String.join(", ", associated.get())) + ")"));
            }
        }

        if (issue.disabledDate() != null && date.isAfter(issue.disabledDate())) {
            problems.add(problem(uid, date, "JIRA_DISABLED", "la issue " + issue.id() + " está de baja desde " + issue.disabledDate().plusDays(1)));
        }

        String status = issue.status() == null ? "" : issue.status().trim().toLowerCase(Locale.ROOT);
        if (status.equals(STATUS_DISCARDED)) {
            LocalDate reached = statusReachedOn(issue.id(), STATUS_DISCARDED);
            if (reached == null || date.isAfter(reached)) {
                problems.add(problem(uid, date, "JIRA_STATUS", "la issue " + issue.id() + " está Discarded"
                        + (reached == null ? " desde hace más de 30 días" : " desde el " + reached)));
            }
        } else if (status.equals(STATUS_DEPLOYED)) {
            LocalDate reached = statusReachedOn(issue.id(), STATUS_DEPLOYED);
            if (reached == null || date.isAfter(reached.plusDays(DEPLOYED_GRACE_DAYS))) {
                problems.add(problem(uid, date, "JIRA_STATUS", "la issue " + issue.id() + " está Deployed"
                        + (reached == null ? " desde hace más de 30 días" : " desde el " + reached + "; solo se admite hasta " + reached.plusDays(DEPLOYED_GRACE_DAYS))));
            }
        }

        boolean isConceptualization = issue.type() != null && issue.type().trim().equalsIgnoreCase(TYPE_CONCEPTUALIZATION_TIME);
        if (req.conceptualizationTime() && !isConceptualization) {
            problems.add(problem(uid, date, "JIRA_TYPE_MISMATCH", "la actividad exige una issue de conceptualization time y " + issue.id() + " es " + issue.type()));
        }
        if (!req.conceptualizationTime() && isConceptualization) {
            problems.add(problem(uid, date, "JIRA_TYPE_MISMATCH", "la issue " + issue.id() + " es de conceptualization time y la actividad no lo es"));
        }

        if (sda != null && !req.conceptualizationTime()) {
            if (req.evolutionaryJiraIssue() && !sda.evolutionary()) {
                problems.add(problem(uid, date, "JIRA_EVOLUTIONARY_MISMATCH", "la actividad exige una feature de evolutivo y el SDA " + sda.id() + " no es evolutivo"));
            }
            if (!req.evolutionaryJiraIssue() && sda.evolutionary()) {
                problems.add(problem(uid, date, "JIRA_EVOLUTIONARY_MISMATCH", "la issue " + issue.id() + " pertenece a un SDA de evolutivo y la actividad no es de evolutivo"));
            }
        }
    }

    /**
     * SDA a los que estaba asociada la issue en esa fecha. Vacío (Optional)
     * si no se puede saber: la issue cambió de SDA DESPUÉS de esa fecha y el
     * catálogo ya no informa la asociación anterior.
     */
    private Optional<Set<String>> sdasAssociatedOn(String issueKey, List<IssueInfo> catalogRows, LocalDate date) {
        Set<String> current = new TreeSet<>();
        for (IssueInfo row : catalogRows) {
            if (notBlank(row.sdaProjectId())) {
                current.add(row.sdaProjectId().trim());
            }
        }
        List<IssueSdaLink> changes = linksByIssue.getOrDefault(issueKey, List.of());
        if (changes.isEmpty()) {
            return Optional.of(current);
        }
        LocalDate lastChange = changes.stream().map(IssueSdaLink::date).max(Comparator.naturalOrder()).orElseThrow();
        if (!date.isBefore(lastChange)) {
            // La fecha es posterior al último cambio: vale lo que dice el catálogo (y el propio cambio)
            Set<String> set = new TreeSet<>(current);
            changes.stream().filter(c -> c.date().equals(lastChange) && notBlank(c.sdaProjectId()))
                    .forEach(c -> set.add(c.sdaProjectId().trim()));
            return Optional.of(set);
        }
        Optional<LocalDate> previous = changes.stream().map(IssueSdaLink::date)
                .filter(d -> !d.isAfter(date)).max(Comparator.naturalOrder());
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        Set<String> set = new TreeSet<>();
        changes.stream().filter(c -> c.date().equals(previous.get()) && notBlank(c.sdaProjectId()))
                .forEach(c -> set.add(c.sdaProjectId().trim()));
        return Optional.of(set);
    }

    /** Fecha más reciente en la que la issue alcanzó ese estado según el histórico (null si no consta). */
    private LocalDate statusReachedOn(String issueKey, String status) {
        return statusChangesByIssue.getOrDefault(issueKey, List.of()).stream()
                .filter(c -> c.status() != null && c.status().trim().equalsIgnoreCase(status))
                .map(StatusChange::date)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Índice absoluto de trimestre (año*4 + trimestre-1) para comparar márgenes. */
    static int quarterIndex(LocalDate date) {
        return date.getYear() * 4 + (date.getMonthValue() - 1) / 3;
    }

    /** Índice de trimestre de un texto tipo "1Q 2022"; null si no se reconoce. */
    static Integer parseQuarter(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = QUARTER.matcher(text.trim());
        if (!m.find()) {
            return null;
        }
        int q;
        int year;
        if (m.group(1) != null) {
            q = Integer.parseInt(m.group(1));
            year = Integer.parseInt(m.group(2));
        } else if (m.group(3) != null) {
            q = Integer.parseInt(m.group(3));
            year = Integer.parseInt(m.group(4));
        } else {
            year = Integer.parseInt(m.group(5));
            q = Integer.parseInt(m.group(6));
        }
        return year * 4 + (q - 1);
    }

    /** Los hashes de persona se comparan sin distinguir mayúsculas (BBVA los devuelve en mayúsculas). */
    private static String hiddenKey(String personId, LocalDate date, String code) {
        return (personId == null ? "" : personId.trim().toUpperCase(Locale.ROOT)) + "|" + date + "|" + code;
    }

    private static ValidationProblem problem(String uid, LocalDate date, String code, String message) {
        return new ValidationProblem(uid, date, code, message);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
