package com.vendor.arconnector.service;

import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.repository.ApiCallRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * "Si la última llamada OK, como máximo 3 llamadas. Si la última llamada KO,
 * como máximo 6 llamadas." Por canal y por día.
 */
class CallQuotaServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), MADRID);
    private static final CallChannel CHANNEL = CallChannel.ACTIVITIES_UPLOAD;

    private final ApiCallRepository repository = mock(ApiCallRepository.class);
    private final CallQuotaService service = new CallQuotaService(repository, CLOCK);

    private static ApiCall call(boolean ok) {
        ApiCall c = new ApiCall(CHANNEL, Instant.parse("2026-09-09T08:00:00Z"), LocalDate.parse("2026-09-09"),
                "f.csv", 1, 1, "x", new byte[0]);
        if (ok) {
            c.markOk(200);
        } else {
            c.markFailed(500, "ko");
        }
        return c;
    }

    private void todayCalls(ApiCall... calls) {
        when(repository.findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(eq(CHANNEL), any(), any()))
                .thenReturn(List.of(calls));
    }

    @Test
    void sinLlamadasHoySePuedeLlamarYElLimiteEs3() {
        todayCalls();

        CallQuotaService.QuotaStatus s = service.status(CHANNEL);

        assertThat(s.canCall()).isTrue();
        assertThat(s.limit()).isEqualTo(3);
        assertThat(s.remaining()).isEqualTo(3);
        assertThat(s.lastOk()).isNull();
    }

    @Test
    void tresLlamadasConLaUltimaOkAgotanLaCuota() {
        todayCalls(call(true), call(true), call(true));

        assertThat(service.canCall(CHANNEL)).isFalse();
        assertThat(service.status(CHANNEL).remaining()).isZero();
    }

    @Test
    void siLaUltimaFueKoElLimiteSubeA6() {
        todayCalls(call(true), call(true), call(false));

        CallQuotaService.QuotaStatus s = service.status(CHANNEL);

        assertThat(s.limit()).isEqualTo(6);
        assertThat(s.canCall()).isTrue();
        assertThat(s.remaining()).isEqualTo(3);
    }

    @Test
    void seisLlamadasConLaUltimaKoAgotanLaCuota() {
        todayCalls(call(false), call(false), call(false), call(false), call(false), call(false));

        assertThat(service.canCall(CHANNEL)).isFalse();
    }

    @Test
    void unKoSeguidoDeUnOkVuelveAlLimiteDe3() {
        todayCalls(call(false), call(true), call(true), call(true));

        assertThat(service.status(CHANNEL).limit()).isEqualTo(3);
        assertThat(service.canCall(CHANNEL)).isFalse();
    }

    @Test
    void elDiaSeCalculaEnLaZonaHorariaDeActivityReport() {
        todayCalls();
        service.status(CHANNEL);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findByChannelAndCalledAtGreaterThanEqualAndCalledAtLessThanOrderByCalledAtAsc(eq(CHANNEL), from.capture(), to.capture());

        // 9 de septiembre en Madrid (UTC+2): de las 22:00Z del día 8 a las 22:00Z del día 9
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-09-08T22:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-09-09T22:00:00Z"));
    }
}
