package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.JiraIssue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JiraIssueRepository extends JpaRepository<JiraIssue, Long> {

    List<JiraIssue> findByJiraIssueIdIn(Collection<String> jiraIssueIds);

    /**
     * Búsqueda para el selector del panel: por id o descripción, sin las dadas
     * de baja. Los filtros vacíos ('') no se aplican.
     *
     * @param q          texto a buscar en el id o la descripción ('' = todas)
     * @param sda        solo issues de ese proyecto SDA ('' = cualquiera)
     * @param withoutSda true = solo issues sin proyecto SDA (features de evolutivo)
     */
    @Query("""
            select i from JiraIssue i
            where i.disabledDate is null
              and (:sda = '' or i.sdaProjectId = :sda)
              and (:withoutSda = false or i.sdaProjectId is null)
              and (:q = ''
                   or lower(i.jiraIssueId) like concat('%', lower(:q), '%')
                   or lower(i.description) like concat('%', lower(:q), '%'))
            order by i.jiraIssueId
            """)
    List<JiraIssue> search(@Param("q") String q, @Param("sda") String sda,
                           @Param("withoutSda") boolean withoutSda, Pageable pageable);
}
