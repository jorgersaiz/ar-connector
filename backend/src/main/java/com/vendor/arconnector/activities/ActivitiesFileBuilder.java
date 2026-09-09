package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.DayKind;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Genera el fichero "IMPUTACIONES" (Vendor -> Activity Report) a partir de
 * días completos por persona, aplicando localmente todas las validaciones
 * descritas en el documento v1.16 para no enviar nada que BBVA vaya a ignorar
 * o rechazar.
 *
 * Formato: CSV separado por ';', UTF-8, una fila por línea de actividad y una
 * fila con solo fecha y person_id por cada día no imputable.
 *
 * Dos modos:
 *  - {@link #build}: estricto, cualquier problema impide generar el fichero.
 *  - {@link #buildSkippingInvalid}: excluye los días con problemas y genera el
 *    resto. Es seguro porque BBVA solo sobreescribe los días que van en el
 *    fichero; un día ausente queda como estaba.
 *
 * Códigos de problema (paralelos a los APV_xx del feedback de BBVA):
 * <pre>
 *  DATE_FUTURE               fecha posterior al día de envío (BBVA no admite futuro)
 *  DATE_OUT_OF_PERIOD        fecha fuera del periodo modificable en la fecha de procesamiento
 *  DUPLICATE_DAY             el mismo día de la misma persona aparece dos veces
 *  EMPTY_WORKED_DAY          día WORKED sin líneas (BBVA lo interpretaría como no imputable)
 *  TREE_NODE_UNKNOWN         task_type_code no existe en el árbol
 *  TREE_NODE_NOT_SELECTABLE  nodo intermedio (can_be_selected = false)
 *  TREE_NODE_DISABLED        nodo dado de baja antes de la fecha de actividad
 *  SDA_REQUIRED / SDA_NOT_ALLOWED
 *  JIRA_REQUIRED / JIRA_NOT_ALLOWED / JIRA_WITHOUT_SDA
 *  NON_SDA_REQUIRED / NON_SDA_NOT_ALLOWED
 *  TIME_OUT_OF_RANGE         minutos fuera de 1..1440
 *  TIME_DAY_EXCEEDED         la suma del día supera 1440
 *  DUPLICATE_LINE            dos líneas idénticas en actividad/proyecto (BBVA devuelve APV_09)
 *  FIELD_INVALID             un campo contiene ';' o saltos de línea
 * </pre>
 */
public final class ActivitiesFileBuilder {

    public static final String SEPARATOR = ";";
    public static final String LINE_END = "\n";
    public static final int MAX_MINUTES_PER_DAY = 1440;

    public static final List<String> HEADER = List.of(
            "date", "person_id",
            "task_level_1_code", "task_level_2_code", "task_level_3_code", "task_level_4_code", "task_level_5_code",
            "sda_project_id", "jira_issue_id", "non_sda_project", "technology_detail", "time"
    );

    /** Resultado del modo tolerante: fichero con los días válidos + problemas de los excluidos. */
    public record LenientResult(ActivitiesFile file, List<ValidationProblem> problems, int excludedDays) {

        /** Claves "uid|fecha" de los días que se han quedado fuera del fichero. */
        public Set<String> excludedDayKeys() {
            Set<String> keys = new HashSet<>();
            for (ValidationProblem p : problems) {
                keys.add(p.uid() + "|" + p.date());
            }
            return keys;
        }
    }

    private record Outcome(ActivitiesFile file, List<ValidationProblem> problems, int excludedDays) {
    }

    private final TaskTree tree;
    private final ModifiablePeriodPolicy periodPolicy;

    public ActivitiesFileBuilder(TaskTree tree, ModifiablePeriodPolicy periodPolicy) {
        this.tree = Objects.requireNonNull(tree);
        this.periodPolicy = Objects.requireNonNull(periodPolicy);
    }

    /**
     * Genera el fichero para un envío realizado en {@code submissionDate}.
     * Si algún día o línea incumple las reglas, no genera nada y lanza
     * {@link ActivitiesFileValidationException} con TODOS los problemas.
     */
    public ActivitiesFile build(List<DaySubmission> days, LocalDate submissionDate) {
        Outcome outcome = compute(days, submissionDate);
        if (!outcome.problems().isEmpty()) {
            throw new ActivitiesFileValidationException(outcome.problems());
        }
        return outcome.file();
    }

    /** Genera el fichero solo con los días válidos; los demás se devuelven como problemas. */
    public LenientResult buildSkippingInvalid(List<DaySubmission> days, LocalDate submissionDate) {
        Outcome outcome = compute(days, submissionDate);
        return new LenientResult(outcome.file(), outcome.problems(), outcome.excludedDays());
    }

    private Outcome compute(List<DaySubmission> days, LocalDate submissionDate) {
        List<ValidationProblem> problems = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        Set<String> seenDays = new HashSet<>();
        int includedDays = 0;
        int excludedDays = 0;

        List<DaySubmission> ordered = days.stream()
                .sorted(Comparator.comparing(DaySubmission::date).thenComparing(d -> d.uid() == null ? "" : d.uid()))
                .toList();

        for (DaySubmission day : ordered) {
            List<ValidationProblem> dayProblems = new ArrayList<>();
            List<String[]> dayRows = validateDay(day, submissionDate, seenDays, dayProblems);
            if (dayProblems.isEmpty()) {
                rows.addAll(dayRows);
                includedDays++;
            } else {
                problems.addAll(dayProblems);
                excludedDays++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(SEPARATOR, HEADER)).append(LINE_END);
        for (String[] row : rows) {
            sb.append(String.join(SEPARATOR, row)).append(LINE_END);
        }
        ActivitiesFile file = new ActivitiesFile(sb.toString().getBytes(StandardCharsets.UTF_8), includedDays, rows.size());
        return new Outcome(file, problems, excludedDays);
    }

    /** Valida un día completo; devuelve sus filas si es válido y deja los problemas en {@code problems}. */
    private List<String[]> validateDay(DaySubmission day, LocalDate submissionDate, Set<String> seenDays,
                                       List<ValidationProblem> problems) {
        if (day.uid() == null || day.uid().isBlank()) {
            problems.add(new ValidationProblem("?", day.date(), "UID_BLANK", "uid vacío"));
            return List.of();
        }
        if (!seenDays.add(day.uid() + "|" + day.date())) {
            problems.add(problem(day, "DUPLICATE_DAY", "el día aparece más de una vez"));
            return List.of();
        }
        if (day.date().isAfter(submissionDate)) {
            problems.add(problem(day, "DATE_FUTURE", "no se admiten imputaciones a futuro"));
            return List.of();
        }
        if (!periodPolicy.canSubmitOn(day.date(), submissionDate)) {
            problems.add(problem(day, "DATE_OUT_OF_PERIOD",
                    "fuera del periodo modificable; último día de envío era "
                            + periodPolicy.lastSubmissionDate(day.date())));
            return List.of();
        }

        String personId = PersonIdHasher.hash(day.uid());

        if (day.kind() == DayKind.NON_WORKING) {
            return List.<String[]>of(nonWorkingRow(day.date(), personId));
        }

        if (day.lines().isEmpty()) {
            problems.add(problem(day, "EMPTY_WORKED_DAY",
                    "día trabajado sin líneas; Activity Report lo trataría como no imputable"));
            return List.of();
        }

        List<String[]> dayRows = new ArrayList<>();
        Set<String> seenLines = new HashSet<>();
        int total = 0;

        for (LineSubmission line : day.lines()) {
            total += line.minutes();
            int before = problems.size();
            validateLine(day, line, problems);
            String key = String.join("|", nz(line.taskTypeCode()), nz(line.sdaProjectId()),
                    nz(line.jiraIssueId()), nz(line.nonSdaProject()));
            if (!seenLines.add(key)) {
                problems.add(problem(day, "DUPLICATE_LINE",
                        "línea repetida para " + line.taskTypeCode() + " (Activity Report devolvería APV_09)"));
            }
            if (problems.size() == before) {
                dayRows.add(workedRow(day.date(), personId, line));
            }
        }
        if (total > MAX_MINUTES_PER_DAY) {
            problems.add(problem(day, "TIME_DAY_EXCEEDED",
                    "la suma del día es " + total + " minutos, máximo " + MAX_MINUTES_PER_DAY));
        }
        return dayRows;
    }

    private void validateLine(DaySubmission day, LineSubmission line, List<ValidationProblem> problems) {
        for (String field : new String[]{line.taskTypeCode(), line.sdaProjectId(), line.jiraIssueId(), line.nonSdaProject()}) {
            if (field != null && (field.contains(SEPARATOR) || field.contains("\n") || field.contains("\r"))) {
                problems.add(problem(day, "FIELD_INVALID", "el campo '" + field + "' contiene ';' o saltos de línea"));
                return;
            }
        }

        if (line.minutes() < 1 || line.minutes() > MAX_MINUTES_PER_DAY) {
            problems.add(problem(day, "TIME_OUT_OF_RANGE",
                    "tiempo " + line.minutes() + " fuera de 1.." + MAX_MINUTES_PER_DAY + " minutos"));
        }

        Optional<TaskTree.Node> found = line.taskTypeCode() == null ? Optional.empty() : tree.find(line.taskTypeCode());
        if (found.isEmpty()) {
            problems.add(problem(day, "TREE_NODE_UNKNOWN", "actividad desconocida: " + line.taskTypeCode()));
            return;
        }
        TaskTree.Node node = found.get();
        if (!node.canBeSelected()) {
            problems.add(problem(day, "TREE_NODE_NOT_SELECTABLE",
                    "la actividad " + node.code() + " es un nodo intermedio, no imputable"));
        }
        if (!node.isEnabledOn(day.date())) {
            problems.add(problem(day, "TREE_NODE_DISABLED",
                    "la actividad " + node.code() + " está dada de baja desde " + node.disabledDate().plusDays(1)));
        }
        TaskTree.Requirements req;
        try {
            req = tree.effectiveRequirementsFor(node.code());
        } catch (RuntimeException e) {
            problems.add(problem(day, "TREE_NODE_UNKNOWN", e.getMessage()));
            return;
        }

        boolean hasSda = notBlank(line.sdaProjectId());
        boolean hasJira = notBlank(line.jiraIssueId());
        boolean hasNonSda = notBlank(line.nonSdaProject());

        if (req.sdaProject() && !hasSda) {
            problems.add(problem(day, "SDA_REQUIRED", "la actividad " + node.code() + " exige proyecto SDA"));
        }
        if (!req.sdaProject() && hasSda) {
            problems.add(problem(day, "SDA_NOT_ALLOWED", "la actividad " + node.code() + " no admite proyecto SDA"));
        }
        if (req.jiraIssue() && !hasJira) {
            problems.add(problem(day, "JIRA_REQUIRED", "la actividad " + node.code() + " exige issue de Jira"));
        }
        if (!req.jiraIssue() && hasJira) {
            problems.add(problem(day, "JIRA_NOT_ALLOWED", "la actividad " + node.code() + " no admite issue de Jira"));
        }
        if (hasJira && !hasSda) {
            problems.add(problem(day, "JIRA_WITHOUT_SDA", "una issue de Jira debe ir acompañada de su proyecto SDA"));
        }
        if (req.nonSdaProject() && !hasNonSda) {
            problems.add(problem(day, "NON_SDA_REQUIRED", "la actividad " + node.code() + " exige proyecto no SDA"));
        }
        if (!req.nonSdaProject() && hasNonSda) {
            problems.add(problem(day, "NON_SDA_NOT_ALLOWED", "la actividad " + node.code() + " no admite proyecto no SDA"));
        }
    }

    private String[] workedRow(LocalDate date, String personId, LineSubmission line) {
        List<String> levels = tree.fiveLevels(line.taskTypeCode());
        return new String[]{
                date.toString(),
                personId,
                levels.get(0), levels.get(1), levels.get(2), levels.get(3), levels.get(4),
                nz(line.sdaProjectId()),
                nz(line.jiraIssueId()),
                nz(line.nonSdaProject()),
                "",                       // technology_detail: informarlo provoca rechazo desde el 13/05/2026
                Integer.toString(line.minutes())
        };
    }

    /** Día no imputable: solo fecha y person_id, el resto de columnas vacías. */
    private String[] nonWorkingRow(LocalDate date, String personId) {
        String[] row = new String[HEADER.size()];
        Arrays.fill(row, "");
        row[0] = date.toString();
        row[1] = personId;
        return row;
    }

    private static ValidationProblem problem(DaySubmission day, String code, String message) {
        return new ValidationProblem(day.uid(), day.date(), code, message);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
