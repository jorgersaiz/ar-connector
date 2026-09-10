package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.UserRegistrationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserRegistrationFeedbackRepository extends JpaRepository<UserRegistrationFeedback, Long> {

    Optional<UserRegistrationFeedback> findBySubmissionDate(LocalDate submissionDate);

    @Query("select f.submissionDate from UserRegistrationFeedback f where f.submissionDate between :from and :to")
    List<LocalDate> findSubmissionDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
