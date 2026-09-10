package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.FeedbackFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeedbackFileRepository extends JpaRepository<FeedbackFile, Long> {

    Optional<FeedbackFile> findBySubmissionDate(LocalDate submissionDate);

    @Query("select f from FeedbackFile f left join fetch f.rows where f.submissionDate = :date")
    Optional<FeedbackFile> findBySubmissionDateWithRows(@Param("date") LocalDate date);

    List<FeedbackFile> findTop60ByOrderBySubmissionDateDesc();

    @Query("select f.submissionDate from FeedbackFile f where f.submissionDate between :from and :to")
    List<LocalDate> findSubmissionDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
