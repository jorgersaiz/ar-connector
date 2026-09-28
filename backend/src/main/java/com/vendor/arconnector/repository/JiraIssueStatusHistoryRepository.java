package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.JiraIssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JiraIssueStatusHistoryRepository extends JpaRepository<JiraIssueStatusHistory, Long> {
    List<JiraIssueStatusHistory> findByJiraIssueKeyIn(Collection<String> jiraIssueKeys);
}
