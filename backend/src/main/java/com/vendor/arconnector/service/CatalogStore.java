package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.catalog.CatalogParsers;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.repository.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reemplazo completo de un catálogo en base de datos, en una sola
 * transacción: si algo falla, se conserva el catálogo anterior.
 *
 * Está separado de {@link CatalogSyncService} para que la transacción
 * envuelva solo el reemplazo y no la descarga HTTP.
 */
@Service
public class CatalogStore {

    private final TaskTypeRepository taskTypes;
    private final SdaProjectRepository sdaProjects;
    private final JiraIssueRepository jiraIssues;
    private final JiraIssueStatusHistoryRepository jiraIssueStatusHistory;
    private final JiraIssueSdaProjectHistoryRepository jiraIssueSdaProjectHistory;
    private final NonSdaProjectRepository nonSdaProjects;
    private final CountryRepository countries;
    private final HiddenTaskTypeRepository hiddenTaskTypes;

    public CatalogStore(TaskTypeRepository taskTypes,
                        SdaProjectRepository sdaProjects,
                        JiraIssueRepository jiraIssues,
                        JiraIssueStatusHistoryRepository jiraIssueStatusHistory,
                        JiraIssueSdaProjectHistoryRepository jiraIssueSdaProjectHistory,
                        NonSdaProjectRepository nonSdaProjects,
                        CountryRepository countries,
                        HiddenTaskTypeRepository hiddenTaskTypes) {
        this.taskTypes = taskTypes;
        this.sdaProjects = sdaProjects;
        this.jiraIssues = jiraIssues;
        this.jiraIssueStatusHistory = jiraIssueStatusHistory;
        this.jiraIssueSdaProjectHistory = jiraIssueSdaProjectHistory;
        this.nonSdaProjects = nonSdaProjects;
        this.countries = countries;
        this.hiddenTaskTypes = hiddenTaskTypes;
    }

    /** Convierte la tabla en entidades y reemplaza el catálogo. Devuelve el nº de filas cargadas. */
    @Transactional
    public int replace(CatalogName catalog, CsvTable table) {
        Instant now = Instant.now();
        return switch (catalog) {
            case TASK_TYPES -> replace(taskTypes, CatalogParsers.taskTypes(table), t -> t.setLastSyncedAt(now));
            case SDA_PROJECTS -> replace(sdaProjects, CatalogParsers.sdaProjects(table), p -> p.setLastSyncedAt(now));
            case JIRA_ISSUES -> replace(jiraIssues, CatalogParsers.jiraIssues(table), i -> i.setLastSyncedAt(now));
            case JIRA_ISSUES_HISTORICAL ->
                    replace(jiraIssueStatusHistory, CatalogParsers.jiraIssuesHistorical(table), h -> h.setLastSyncedAt(now));
            case JIRA_ISSUE_SDA_PROJECTS_HISTORICAL ->
                    replace(jiraIssueSdaProjectHistory, CatalogParsers.jiraIssueSdaProjectsHistorical(table), h -> h.setLastSyncedAt(now));
            case NON_SDA_PROJECTS -> replace(nonSdaProjects, CatalogParsers.nonSdaProjects(table), p -> p.setLastSyncedAt(now));
            case COUNTRIES -> replace(countries, CatalogParsers.countries(table), c -> c.setLastSyncedAt(now));
            case HIDDEN_TASK_TYPES -> replace(hiddenTaskTypes, CatalogParsers.hiddenTaskTypes(table), h -> h.setLastSyncedAt(now));
        };
    }

    private static <T, ID> int replace(JpaRepository<T, ID> repository, List<T> entities, Consumer<T> stamp) {
        entities.forEach(stamp);
        repository.deleteAllInBatch();
        repository.flush();
        repository.saveAll(entities);
        repository.flush();
        return entities.size();
    }
}
