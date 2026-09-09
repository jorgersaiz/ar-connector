package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivityDayRepository extends JpaRepository<ActivityDay, Long> {

    Optional<ActivityDay> findByPersonAndActivityDate(Person person, LocalDate activityDate);

    /** Días del rango con sus líneas y persona ya cargadas, para construir el fichero sin N+1. */
    @Query("""
            select distinct d from ActivityDay d
            join fetch d.person
            left join fetch d.lines
            where d.activityDate between :from and :to
            """)
    List<ActivityDay> findInRangeWithLines(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
