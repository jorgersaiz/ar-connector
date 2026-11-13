package com.vendor.arconnector.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MissedRunsTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String UPLOADS = "0 0 13,18,21 * * *";
    private static final String NOTIFICATIONS = "0 0 9 * * *";

    private static ZonedDateTime at(String local) {
        return java.time.LocalDateTime.parse(local).atZone(MADRID);
    }

    private static Instant instant(String local) {
        return at(local).toInstant();
    }

    @Test
    void laUltimaHoraProgramadaEsLaMasRecienteQueNoEsFutura() {
        assertThat(MissedRuns.lastScheduledAtOrBefore(UPLOADS, at("2026-11-13T15:20:00"))).contains(at("2026-11-13T13:00:00"));
        assertThat(MissedRuns.lastScheduledAtOrBefore(UPLOADS, at("2026-11-13T08:00:00"))).contains(at("2026-11-12T21:00:00"));
        assertThat(MissedRuns.lastScheduledAtOrBefore(UPLOADS, at("2026-11-13T13:00:00"))).contains(at("2026-11-13T13:00:00"));
        assertThat(MissedRuns.nextScheduledAfter(UPLOADS, at("2026-11-13T15:20:00"))).contains(at("2026-11-13T18:00:00"));
    }

    @Test
    void siSeEjecutoDespuesDeSuUltimaHoraNoToca() {
        assertThat(MissedRuns.due(UPLOADS, instant("2026-11-13T13:00:01"), at("2026-11-13T15:00:00"), false)).isEmpty();
    }

    @Test
    void siSeSaltoSuUltimaHoraTocaUnaSolaVez() {
        // última ejecución ayer a las 18:00; el PC estuvo apagado a las 21:00 y hasta hoy a las 8:00
        assertThat(MissedRuns.due(UPLOADS, instant("2026-11-12T18:00:00"), at("2026-11-13T08:00:00"), false))
                .contains(at("2026-11-12T21:00:00"));
        // tras recuperarla, ya no vuelve a tocar hasta las 13:00
        assertThat(MissedRuns.due(UPLOADS, instant("2026-11-13T08:01:00"), at("2026-11-13T12:59:00"), false)).isEmpty();
    }

    @Test
    void unFinDeSemanaApagadoSeRecuperaConUnSoloEnvio() {
        // viernes 13 a las 21:00 fue el último envío; se enciende el lunes 16 a las 8:00
        assertThat(MissedRuns.due(UPLOADS, instant("2026-11-13T21:00:00"), at("2026-11-16T08:00:00"), false))
                .contains(at("2026-11-15T21:00:00"));
    }

    @Test
    void siNuncaSeHaEjecutadoToca() {
        assertThat(MissedRuns.due(UPLOADS, null, at("2026-11-13T15:00:00"), false)).contains(at("2026-11-13T13:00:00"));
    }

    @Test
    void losAvisosDeUnDiaAnteriorNoSeRecuperan() {
        // el PC se enciende a las 8:00: los avisos de ayer a las 9:00 ya no se mandan
        assertThat(MissedRuns.due(NOTIFICATIONS, instant("2026-11-11T09:00:00"), at("2026-11-13T08:00:00"), true)).isEmpty();
        // pero si se enciende a las 11:00, los de hoy sí
        assertThat(MissedRuns.due(NOTIFICATIONS, instant("2026-11-12T09:00:00"), at("2026-11-13T11:00:00"), true))
                .contains(at("2026-11-13T09:00:00"));
    }

    @Test
    void elCambioDeHoraNoDuplicaNiPierdeEjecuciones() {
        // 25/10/2026: a las 3:00 vuelven a ser las 2:00 en Madrid
        assertThat(MissedRuns.lastScheduledAtOrBefore("0 0 4 * * *", at("2026-10-25T10:00:00"))).contains(at("2026-10-25T04:00:00"));
        assertThat(MissedRuns.due("0 0 4 * * *", instant("2026-10-25T04:00:00"), at("2026-10-25T23:00:00"), false)).isEmpty();
    }
}
