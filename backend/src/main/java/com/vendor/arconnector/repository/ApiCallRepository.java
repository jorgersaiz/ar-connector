package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ApiCallRepository extends JpaRepository<ApiCall, Long> {

    /** Llamadas de un canal en [from, to), en orden cronológico. */
    List<ApiCall> findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(
            CallChannel channel, Instant from, Instant to);

    List<ApiCall> findTop50ByChannelOrderByCalledAtDesc(CallChannel channel);

    /** Fechas de envío con al menos una llamada OK en el rango (candidatas a tener feedback). */
    @Query("""
            select distinct c.submissionDate from ApiCall c
            where c.channel = :channel and c.ok = true and c.submissionDate between :from and :to
            order by c.submissionDate
            """)
    List<LocalDate> findSuccessfulSubmissionDates(@Param("channel") CallChannel channel,
                                                  @Param("from") LocalDate from, @Param("to") LocalDate to);
}
