package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByDateBetween(LocalDate from, LocalDate to);
    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
    List<Holiday> findByGeographyAndDateBetweenOrderByDateAsc(String geography, LocalDate from, LocalDate to);
}
