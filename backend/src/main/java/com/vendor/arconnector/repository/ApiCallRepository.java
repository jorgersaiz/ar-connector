package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ApiCallRepository extends JpaRepository<ApiCall, Long> {

    /** Llamadas de un canal en [from, to), en orden cronológico. */
    List<ApiCall> findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(
            CallChannel channel, Instant from, Instant to);

    List<ApiCall> findTop50ByChannelOrderByCalledAtDesc(CallChannel channel);
}
