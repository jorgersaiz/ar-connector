package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.JiraIssueSdaProjectHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JiraIssueSdaProjectHistoryRepository extends JpaRepository<JiraIssueSdaProjectHistory, Long> {
    List<JiraIssueSdaProjectHistory> findByJiraIssueKeyIn(Collection<String> jiraIssueKeys);
}
