package com.vendor.arconnector.service;

import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.catalog.CatalogParsers;
import com.vendor.arconnector.catalog.CsvTable;
import com.vendor.arconnector.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reemplazo completo de un catálogo en base de datos, en una sola
 * transacción: si algo falla, se conserva el catálogo anterior.
 *
 * Se escribe con JDBC por lotes y no con JPA porque los catálogos reales son
 * grandes (issues de Jira: ~270.000 filas; su histórico: ~185.000) y JPA
 * haría una consulta y una inserción por fila.
 *
 * Está separado de {@link CatalogSyncService} para que la transacción
 * envuelva solo el reemplazo y no la descarga HTTP.
 */
@Service
public class CatalogStore {

    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;

    public CatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Convierte la tabla en filas y reemplaza el catálogo. Devuelve el nº de filas cargadas. */
    @Transactional
    public int replace(CatalogName catalog, CsvTable table) {
        Timestamp now = Timestamp.from(Instant.now());
        return switch (catalog) {
            case TASK_TYPES -> replace("task_type", """
                    insert into task_type (task_type_id, task_type_parent_id, description_es, description_en, help_es, help_en,
                        requires_sda_project, requires_jira_issue, requires_non_sda_project, requires_technology_detail,
                        sort_order, disabled_date, can_be_selected, requires_evolutionary_sda,
                        requires_evolutionary_jira_issue, requires_conceptualization_time, last_synced_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, CatalogParsers.taskTypes(table), (ps, t) -> {
                ps.setString(1, t.getTaskTypeId());
                ps.setString(2, t.getTaskTypeParentId());
                ps.setString(3, cut(t.getDescriptionEs(), 500));
                ps.setString(4, cut(t.getDescriptionEn(), 500));
                ps.setString(5, t.getHelpEs());
                ps.setString(6, t.getHelpEn());
                ps.setBoolean(7, t.isRequiresSdaProject());
                ps.setBoolean(8, t.isRequiresJiraIssue());
                ps.setBoolean(9, t.isRequiresNonSdaProject());
                ps.setBoolean(10, t.isRequiresTechnologyDetail());
                if (t.getSortOrder() == null) {
                    ps.setNull(11, Types.INTEGER);
                } else {
                    ps.setInt(11, t.getSortOrder());
                }
                setDate(ps, 12, t.getDisabledDate());
                ps.setBoolean(13, t.isCanBeSelected());
                ps.setBoolean(14, t.isRequiresEvolutionarySda());
                ps.setBoolean(15, t.isRequiresEvolutionaryJiraIssue());
                ps.setBoolean(16, t.isRequiresConceptualizationTime());
                ps.setTimestamp(17, now);
            });
            case SDA_PROJECTS -> replace("sda_project", """
                    insert into sda_project (sda_project_id, description, start_quarter, end_quarter, status, disabled_date,
                        country, last_modified_status_date, evolutionary_sda, last_synced_at)
                    values (?,?,?,?,?,?,?,?,?,?)
                    """, CatalogParsers.sdaProjects(table), (ps, p) -> {
                ps.setString(1, p.getSdaProjectId());
                ps.setString(2, cut(p.getDescription(), 500));
                ps.setString(3, cut(p.getStartQuarter(), 16));
                ps.setString(4, cut(p.getEndQuarter(), 16));
                ps.setString(5, cut(p.getStatus(), 64));
                setDate(ps, 6, p.getDisabledDate());
                ps.setString(7, cut(p.getCountry(), 128));
                setDate(ps, 8, p.getLastModifiedStatusDate());
                ps.setBoolean(9, p.isEvolutionarySda());
                ps.setTimestamp(10, now);
            });
            case JIRA_ISSUES -> replace("jira_issue", """
                    insert into jira_issue (jira_issue_id, description, sda_project_id, status, geography, disabled_date, type, last_synced_at)
                    values (?,?,?,?,?,?,?,?)
                    """, CatalogParsers.jiraIssues(table), (ps, i) -> {
                ps.setString(1, i.getJiraIssueId());
                ps.setString(2, cut(i.getDescription(), 500));
                ps.setString(3, i.getSdaProjectId());
                ps.setString(4, cut(i.getStatus(), 64));
                ps.setString(5, cut(i.getGeography(), 64));
                setDate(ps, 6, i.getDisabledDate());
                ps.setString(7, cut(i.getType(), 64));
                ps.setTimestamp(8, now);
            });
            case JIRA_ISSUES_HISTORICAL -> replace("jira_issue_status_history", """
                    insert into jira_issue_status_history (jira_issue_key, status, status_change_date, last_synced_at) values (?,?,?,?)
                    """, CatalogParsers.jiraIssuesHistorical(table), (ps, h) -> {
                ps.setString(1, h.getJiraIssueKey());
                ps.setString(2, cut(h.getStatus(), 64));
                setDate(ps, 3, h.getStatusChangeDate());
                ps.setTimestamp(4, now);
            });
            case JIRA_ISSUE_SDA_PROJECTS_HISTORICAL -> replace("jira_issue_sda_project_history", """
                    insert into jira_issue_sda_project_history (jira_issue_key, sda_project_id, change_date, last_synced_at) values (?,?,?,?)
                    """, CatalogParsers.jiraIssueSdaProjectsHistorical(table), (ps, h) -> {
                ps.setString(1, h.getJiraIssueKey());
                ps.setString(2, h.getSdaProjectId());
                setDate(ps, 3, h.getChangeDate());
                ps.setTimestamp(4, now);
            });
            case NON_SDA_PROJECTS -> replace("non_sda_project", """
                    insert into non_sda_project (description, disabled_date, last_synced_at) values (?,?,?)
                    """, CatalogParsers.nonSdaProjects(table), (ps, p) -> {
                ps.setString(1, p.getDescription());
                setDate(ps, 2, p.getDisabledDate());
                ps.setTimestamp(3, now);
            });
            case COUNTRIES -> replace("country", """
                    insert into country (country_code, ended_margin, onhold_margin, sda_country, timezone, last_synced_at) values (?,?,?,?,?,?)
                    """, CatalogParsers.countries(table), (ps, c) -> {
                ps.setString(1, c.getCountryCode());
                ps.setInt(2, c.getEndedMargin());
                ps.setInt(3, c.getOnholdMargin());
                ps.setString(4, c.getSdaCountry());
                ps.setString(5, c.getTimezone());
                ps.setTimestamp(6, now);
            });
            case HIDDEN_TASK_TYPES -> replace("hidden_task_type", """
                    insert into hidden_task_type (person_id, hidden_date, geography, task_type_code, last_synced_at) values (?,?,?,?,?)
                    """, CatalogParsers.hiddenTaskTypes(table), (ps, h) -> {
                ps.setString(1, h.getPersonId());
                setDate(ps, 2, h.getDate());
                ps.setString(3, cut(h.getGeography(), 64));
                ps.setString(4, h.getTaskTypeCode());
                ps.setTimestamp(5, now);
            });
        };
    }

    private <T> int replace(String tableName, String insertSql, List<T> rows, ParameterizedPreparedStatementSetter<T> setter) {
        jdbc.update("delete from " + tableName);
        jdbc.batchUpdate(insertSql, rows, BATCH, setter);
        return rows.size();
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate date) throws SQLException {
        if (date == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(date));
        }
    }

    /** Los textos libres de BBVA no deben tumbar la carga de un catálogo por exceder la columna. */
    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
