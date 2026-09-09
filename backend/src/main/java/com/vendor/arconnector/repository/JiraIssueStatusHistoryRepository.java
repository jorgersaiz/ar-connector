package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.JiraIssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraIssueStatusHistoryRepository extends JpaRepository<JiraIssueStatusHistory, Long> {
}
