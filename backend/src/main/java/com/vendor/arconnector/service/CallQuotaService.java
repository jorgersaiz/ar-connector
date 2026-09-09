package com.vendor.arconnector.service;

import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.repository.ApiCallRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cuota diaria de llamadas de subida, según "Consideraciones endpoints":
 *
 * <pre>
 *   El número de llamadas diarias depende del resultado de la última llamada realizada en el día:
 *     - si la última llamada fue OK, como máximo 3 llamadas;
 *     - si la última llamada fue KO, como máximo 6 llamadas.
 * </pre>
 *
 * Se aplica por canal (imputaciones y registro de usuarios por separado) y el
 * "día" es el de la zona horaria de Activity Report ({@link Clock}).
 */
@Service
public class CallQuotaService {

    public static final int MAX_CALLS_AFTER_OK = 3;
    public static final int MAX_CALLS_AFTER_KO = 6;

    /**
     * @param callsToday llamadas ya hechas hoy en el canal
     * @param limit      límite aplicable según el resultado de la última llamada
     * @param lastOk     resultado de la última llamada (null si no hay ninguna hoy)
     */
    public record QuotaStatus(CallChannel channel, LocalDate day, int callsToday, int limit, Boolean lastOk) {
        public int remaining() {
            return Math.max(0, limit - callsToday);
        }

        public boolean canCall() {
            return callsToday < limit;
        }
    }

    private final ApiCallRepository apiCallRepository;
    private final Clock clock;

    public CallQuotaService(ApiCallRepository apiCallRepository, Clock clock) {
        this.apiCallRepository = apiCallRepository;
        this.clock = clock;
    }

    public QuotaStatus status(CallChannel channel) {
        LocalDate today = LocalDate.now(clock);
        List<ApiCall> calls = callsOn(channel, today);
        Boolean lastOk = calls.isEmpty() ? null : calls.get(calls.size() - 1).isOk();
        int limit = Boolean.FALSE.equals(lastOk) ? MAX_CALLS_AFTER_KO : MAX_CALLS_AFTER_OK;
        return new QuotaStatus(channel, today, calls.size(), limit, lastOk);
    }

    public boolean canCall(CallChannel channel) {
        return status(channel).canCall();
    }

    /** Llamadas del canal en el día indicado, en orden cronológico. */
    public List<ApiCall> callsOn(CallChannel channel, LocalDate day) {
        Instant from = day.atStartOfDay(clock.getZone()).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return apiCallRepository.findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(channel, from, to);
    }
}
