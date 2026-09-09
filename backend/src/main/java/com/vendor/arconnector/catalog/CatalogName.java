package com.vendor.arconnector.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogos que expone GET /catalogues/&lt;catalogue&gt; según el documento v1.16.
 * "other_technologies" desapareció en la v1.16 y no se sincroniza.
 */
public enum CatalogName {
    TASK_TYPES("task_types"),
    SDA_PROJECTS("sda_projects"),
    JIRA_ISSUES("jira_issues"),
    JIRA_ISSUES_HISTORICAL("jira_issues_historical"),
    JIRA_ISSUE_SDA_PROJECTS_HISTORICAL("jira_issue_sda_projects_historical"),
    NON_SDA_PROJECTS("non_sda_projects"),
    COUNTRIES("countries"),
    HIDDEN_TASK_TYPES("hidden_task_types");

    private final String apiName;

    CatalogName(String apiName) {
        this.apiName = apiName;
    }

    /** Nombre tal como se usa en la URL del endpoint y en el fichero local de pruebas. */
    public String apiName() {
        return apiName;
    }

    public static Optional<CatalogName> fromApiName(String name) {
        return Arrays.stream(values()).filter(c -> c.apiName.equalsIgnoreCase(name)).findFirst();
    }
}
