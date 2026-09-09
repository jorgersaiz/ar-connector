package com.vendor.arconnector.catalog;

import com.vendor.arconnector.domain.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Conversión de cada catálogo CSV a sus entidades, con las columnas que
 * describe el documento v1.16. Lógica pura, sin Spring.
 *
 * Política de errores: se recogen todos los problemas de fila y se lanza una
 * única {@link CatalogParseException}; el catálogo se descarta entero.
 */
public final class CatalogParsers {

    private static final int MAX_REPORTED_ERRORS = 20;

    private CatalogParsers() {
    }

    // ÁRBOL DE ACTIVIDADES
    public static List<TaskType> taskTypes(CsvTable table) {
        table.requireColumns("task_type_id", "task_type_parent_id", "description_es", "description_en",
                "requires_sda_project", "requires_jira_issue", "requires_non_sda_project", "can_be_selected");
        return parseRows(table, row -> {
            TaskType t = new TaskType();
            t.setTaskTypeId(row.requiredText("task_type_id"));
            t.setTaskTypeParentId(row.optionalText("task_type_parent_id"));
            t.setDescriptionEs(row.optionalText("description_es"));
            t.setDescriptionEn(row.optionalText("description_en"));
            t.setHelpEs(row.optionalText("help_es"));
            t.setHelpEn(row.optionalText("help_en"));
            t.setRequiresSdaProject(row.bool("requires_sda_project"));
            t.setRequiresJiraIssue(row.bool("requires_jira_issue"));
            t.setRequiresNonSdaProject(row.bool("requires_non_sda_project"));
            t.setRequiresTechnologyDetail(row.bool("requires_technology_detail"));
            t.setSortOrder(row.optionalInt("order"));
            t.setDisabledDate(row.optionalDate("disabled_date"));
            t.setCanBeSelected(row.bool("can_be_selected"));
            t.setRequiresEvolutionarySda(row.bool("requires_evolutionary_sda"));
            t.setRequiresEvolutionaryJiraIssue(row.bool("requires_evolutionary_jira_issue"));
            t.setRequiresConceptualizationTime(row.bool("requires_conceptualization_time"));
            return t;
        }, TaskType::getTaskTypeId);
    }

    // PROYECTOS SDA
    public static List<SdaProject> sdaProjects(CsvTable table) {
        table.requireColumns("sda_project_id", "description", "status", "disabled_date", "country");
        return parseRows(table, row -> {
            SdaProject p = new SdaProject();
            p.setSdaProjectId(row.requiredText("sda_project_id"));
            p.setDescription(row.optionalText("description"));
            p.setStartQuarter(row.optionalText("start_date"));
            p.setEndQuarter(row.optionalText("end_date"));
            p.setStatus(row.optionalText("status"));
            p.setDisabledDate(row.optionalDate("disabled_date"));
            p.setCountry(row.optionalText("country"));
            p.setLastModifiedStatusDate(row.optionalDate("last_modified_status_date"));
            p.setEvolutionarySda(row.bool("evolutionary_sda"));
            return p;
        }, SdaProject::getSdaProjectId);
    }

    // ISSUES DE PROYECTOS SDA
    public static List<JiraIssue> jiraIssues(CsvTable table) {
        table.requireColumns("jira_issue_id", "description", "sda_project_id", "status", "disabled_date");
        return parseRows(table, row -> {
            JiraIssue i = new JiraIssue();
            i.setJiraIssueId(row.requiredText("jira_issue_id"));
            i.setDescription(row.optionalText("description"));
            i.setSdaProjectId(row.optionalText("sda_project_id"));
            i.setStatus(row.optionalText("status"));
            i.setGeography(row.optionalText("geography"));
            i.setDisabledDate(row.optionalDate("disabled_date"));
            i.setType(row.optionalText("type"));
            return i;
        }, JiraIssue::getJiraIssueId);
    }

    // HISTÓRICO DE ESTADOS DE ISSUES
    public static List<JiraIssueStatusHistory> jiraIssuesHistorical(CsvTable table) {
        table.requireColumns("jira_issue_key", "status", "status_change_date");
        return parseRows(table, row -> new JiraIssueStatusHistory(
                row.requiredText("jira_issue_key"),
                row.requiredText("status"),
                row.requiredDate("status_change_date")
        ), null);
    }

    // HISTÓRICO DE FEATURES Y PROYECTOS SDA
    public static List<JiraIssueSdaProjectHistory> jiraIssueSdaProjectsHistorical(CsvTable table) {
        table.requireColumns("jira_issue_key", "sda_project_id", "date");
        return parseRows(table, row -> new JiraIssueSdaProjectHistory(
                row.requiredText("jira_issue_key"),
                row.requiredText("sda_project_id"),
                row.requiredDate("date")
        ), null);
    }

    // PROYECTOS NO SDA
    public static List<NonSdaProject> nonSdaProjects(CsvTable table) {
        table.requireColumns("description");
        return parseRows(table, row -> new NonSdaProject(
                row.requiredText("description"),
                row.optionalDate("disabled_date")
        ), NonSdaProject::getDescription);
    }

    // PAÍSES
    public static List<Country> countries(CsvTable table) {
        table.requireColumns("country_code", "ended_margin", "onhold_margin");
        return parseRows(table, row -> {
            Country c = new Country();
            c.setCountryCode(row.requiredText("country_code"));
            c.setEndedMargin(row.requiredInt("ended_margin"));
            c.setOnholdMargin(row.requiredInt("onhold_margin"));
            c.setSdaCountry(row.optionalText("sda_country"));
            c.setTimezone(row.optionalText("timezone"));
            return c;
        }, Country::getCountryCode);
    }

    // NODOS OCULTOS DEL ÁRBOL
    public static List<HiddenTaskType> hiddenTaskTypes(CsvTable table) {
        table.requireColumns("person_id", "date", "task_type_code");
        return parseRows(table, row -> new HiddenTaskType(
                row.requiredText("person_id"),
                row.requiredDate("date"),
                row.optionalText("geography"),
                row.requiredText("task_type_code")
        ), null);
    }

    /**
     * Recorre las filas aplicando el mapeo, acumula los errores de fila y, si
     * se indica {@code keyOf}, rechaza claves repetidas (que romperían el
     * reemplazo del catálogo en base de datos).
     */
    private static <T> List<T> parseRows(CsvTable table, Function<CsvRow, T> mapper, Function<T, String> keyOf) {
        List<T> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (CsvRow row : table.rows()) {
            try {
                T entity = mapper.apply(row);
                if (keyOf != null) {
                    String key = keyOf.apply(entity);
                    if (!seenKeys.add(key)) {
                        errors.add("línea " + row.lineNumber() + ": identificador repetido '" + key + "'");
                        continue;
                    }
                }
                result.add(entity);
            } catch (CatalogRowException e) {
                errors.add(e.getMessage());
            }
            if (errors.size() > MAX_REPORTED_ERRORS) {
                errors.add("... (más errores omitidos)");
                break;
            }
        }
        if (!errors.isEmpty()) {
            throw new CatalogParseException(errors);
        }
        return result;
    }
}
