package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.CatalogRules;
import com.vendor.arconnector.activities.DaySubmission;
import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Construye las {@link CatalogRules} para validar un conjunto de días.
 *
 * Los catálogos reales son grandes (270.000 issues de Jira, 185.000 cambios
 * de estado), así que solo se cargan los proyectos e issues que aparecen en
 * las líneas a validar, no el catálogo entero.
 */
@Service
public class CatalogRulesService {

    private static final int IN_CHUNK = 500;

    private final SdaProjectRepository sdaProjects;
    private final JiraIssueRepository jiraIssues;
    private final CountryRepository countries;
    private final JiraIssueStatusHistoryRepository statusHistory;
    private final JiraIssueSdaProjectHistoryRepository sdaHistory;
    private final NonSdaProjectRepository nonSdaProjects;
    private final HiddenTaskTypeRepository hiddenTaskTypes;

    public CatalogRulesService(SdaProjectRepository sdaProjects, JiraIssueRepository jiraIssues, CountryRepository countries,
                               JiraIssueStatusHistoryRepository statusHistory, JiraIssueSdaProjectHistoryRepository sdaHistory,
                               NonSdaProjectRepository nonSdaProjects, HiddenTaskTypeRepository hiddenTaskTypes) {
        this.sdaProjects = sdaProjects;
        this.jiraIssues = jiraIssues;
        this.countries = countries;
        this.statusHistory = statusHistory;
        this.sdaHistory = sdaHistory;
        this.nonSdaProjects = nonSdaProjects;
        this.hiddenTaskTypes = hiddenTaskTypes;
    }

    @Transactional(readOnly = true)
    public CatalogRules loadFor(Collection<DaySubmission> days) {
        Set<String> sdaIds = new TreeSet<>();
        Set<String> issueIds = new TreeSet<>();
        Set<String> nonSdaNames = new TreeSet<>();
        for (DaySubmission day : days) {
            for (LineSubmission line : day.lines()) {
                add(sdaIds, line.sdaProjectId());
                add(issueIds, line.jiraIssueId());
                add(nonSdaNames, line.nonSdaProject());
            }
        }
        return CatalogRules.fromCatalog(
                inChunks(sdaIds, sdaProjects::findAllById),
                inChunks(issueIds, jiraIssues::findByJiraIssueIdIn),
                countries.findAll(),
                inChunks(issueIds, statusHistory::findByJiraIssueKeyIn),
                inChunks(issueIds, sdaHistory::findByJiraIssueKeyIn),
                inChunks(nonSdaNames, nonSdaProjects::findAllById),
                hiddenTaskTypes.findAll());   // solo trae las personas del proveedor: es pequeño
    }

    private static void add(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value.trim());
        }
    }

    private static <T> List<T> inChunks(Set<String> ids, Function<List<String>, ? extends Iterable<T>> query) {
        List<T> result = new ArrayList<>();
        List<String> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            query.apply(all.subList(i, Math.min(all.size(), i + IN_CHUNK))).forEach(result::add);
        }
        return result;
    }
}
