package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.CatalogRules;
import com.vendor.arconnector.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga los catálogos sincronizados y construye las {@link CatalogRules} en memoria. */
@Service
public class CatalogRulesService {

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
    public CatalogRules load() {
        return CatalogRules.fromCatalog(sdaProjects.findAll(), jiraIssues.findAll(), countries.findAll(),
                statusHistory.findAll(), sdaHistory.findAll(), nonSdaProjects.findAll(), hiddenTaskTypes.findAll());
    }
}
