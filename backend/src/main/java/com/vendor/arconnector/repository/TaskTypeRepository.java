package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTypeRepository extends JpaRepository<TaskType, String> {
}
