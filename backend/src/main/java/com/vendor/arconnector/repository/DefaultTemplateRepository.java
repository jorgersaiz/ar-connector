package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.DefaultTemplate;
import com.vendor.arconnector.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DefaultTemplateRepository extends JpaRepository<DefaultTemplate, Long> {

    @Query("""
            select distinct t from DefaultTemplate t
            join fetch t.person p
            left join fetch t.lines
            where p.active = true
            """)
    List<DefaultTemplate> findAllOfActivePersonsWithLines();

    @Query("""
            select distinct t from DefaultTemplate t
            left join fetch t.lines
            where t.person = :person
            order by t.validFrom
            """)
    List<DefaultTemplate> findByPersonWithLines(@Param("person") Person person);
}
