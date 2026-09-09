package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.JiraIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraIssueRepository extends JpaRepository<JiraIssue, String> {
}
