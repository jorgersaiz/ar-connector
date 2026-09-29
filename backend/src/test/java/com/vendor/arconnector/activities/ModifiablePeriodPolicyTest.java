package com.vendor.arconnector.activities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del documento:
 *  - 1ª quincena: modificable entre el día 1 y el día 20.
 *  - 2ª quincena: modificable entre el día 15 y el día 5 del mes+1.
 *  - La validación es sobre el día de procesamiento (N+1 respecto al envío),
 *    por lo que el último día de envío es el 19 y el 4 del mes siguiente.
 *  - No se admiten imputaciones a futuro.
 * Confirmado por BBVA: se puede enviar cualquier día, también sábados y
 * domingos, y el fichero se procesa al día siguiente.
 */
class ModifiablePeriodPolicyTest {

    private final ModifiablePeriodPolicy policy = new ModifiablePeriodPolicy();

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void primeraQuincenaSeProcesaDel1Al20() {
        assertThat(policy.modifiablePeriodFor(d("2026-03-10")))
                .isEqualTo(new DateRange(d("2026-03-01"), d("2026-03-20")));
        assertThat(policy.modifiablePeriodFor(d("2026-03-15")))
                .isEqualTo(new DateRange(d("2026-03-01"), d("2026-03-20")));
    }

    @Test
    void segundaQuincenaSeProcesaDel15Al5DelMesSiguiente() {
        assertThat(policy.modifiablePeriodFor(d("2026-03-16")))
                .isEqualTo(new DateRange(d("2026-03-15"), d("2026-04-05")));
        assertThat(policy.modifiablePeriodFor(d("2026-03-31")))
                .isEqualTo(new DateRange(d("2026-03-15"), d("2026-04-05")));
        // Diciembre -> enero del año siguiente
        assertThat(policy.modifiablePeriodFor(d("2026-12-20")))
                .isEqualTo(new DateRange(d("2026-12-15"), d("2027-01-05")));
    }

    @Test
    void ultimoDiaDeEnvioEsElAnteriorAlUltimoDiaDeProcesamiento() {
        assertThat(policy.lastSubmissionDate(d("2026-03-10"))).isEqualTo(d("2026-03-19"));   // jueves
        assertThat(policy.lastSubmissionDate(d("2026-02-20"))).isEqualTo(d("2026-03-04"));   // miércoles
    }

    @Test
    void elCorteNoSeMueveAunqueCaigaEnFinDeSemana() {
        // 19 de septiembre de 2026 es sábado
        assertThat(policy.lastSubmissionDate(d("2026-09-10"))).isEqualTo(d("2026-09-19"));
        // 4 de octubre de 2026 es domingo
        assertThat(policy.lastSubmissionDate(d("2026-09-20"))).isEqualTo(d("2026-10-04"));

        assertThat(policy.canSubmitOn(d("2026-09-10"), d("2026-09-19"))).isTrue();
        assertThat(policy.canSubmitOn(d("2026-09-10"), d("2026-09-20"))).isFalse();
        assertThat(policy.canSubmitOn(d("2026-09-20"), d("2026-10-04"))).isTrue();
        assertThat(policy.canSubmitOn(d("2026-09-20"), d("2026-10-05"))).isFalse();
    }

    @Test
    void elDia19SePuedeEnviarLaPrimeraQuincenaPeroElDia20YaNo() {
        assertThat(policy.canSubmitOn(d("2026-03-10"), d("2026-03-19"))).isTrue();
        assertThat(policy.canSubmitOn(d("2026-03-10"), d("2026-03-20"))).isFalse();
    }

    @Test
    void elDia4SePuedeEnviarLaSegundaQuincenaPeroElDia5YaNo() {
        assertThat(policy.canSubmitOn(d("2026-02-27"), d("2026-03-04"))).isTrue();
        assertThat(policy.canSubmitOn(d("2026-02-27"), d("2026-03-05"))).isFalse();
    }

    @Test
    void noSeAdmiteFuturo() {
        assertThat(policy.canSubmitOn(d("2026-03-12"), d("2026-03-11"))).isFalse();
        assertThat(policy.canSubmitOn(d("2026-03-11"), d("2026-03-11"))).isTrue();
    }

    @Test
    void periodoAbiertoElDia3IncluyeSegundaQuincenaAnteriorYLoQueVaDeMes() {
        List<LocalDate> dates = policy.submittableActivityDates(d("2026-03-04"));

        assertThat(dates).first().isEqualTo(d("2026-02-16"));
        assertThat(dates).last().isEqualTo(d("2026-03-04"));
        assertThat(dates).doesNotContain(d("2026-02-15"));
        assertThat(dates).hasSize(13 + 4); // 16..28 de febrero + 1..4 de marzo
    }

    @Test
    void periodoAbiertoElDia17IncluyeTodoElMesHastaHoy() {
        List<LocalDate> dates = policy.submittableActivityDates(d("2026-03-17"));

        assertThat(dates).first().isEqualTo(d("2026-03-01"));
        assertThat(dates).last().isEqualTo(d("2026-03-17"));
        assertThat(dates).hasSize(17);
    }

    @Test
    void periodoAbiertoElDia20YaNoIncluyeLaPrimeraQuincena() {
        List<LocalDate> dates = policy.submittableActivityDates(d("2026-03-20"));

        assertThat(dates).first().isEqualTo(d("2026-03-16"));
        assertThat(dates).last().isEqualTo(d("2026-03-20"));
    }

    @Test
    void unEnvioRealDel24DeSeptiembreSoloLlevaLaSegundaQuincena() {
        // El fichero de ejemplo de BBVA enviado el 24/09/2026 contiene del 16 al 24
        List<LocalDate> dates = policy.submittableActivityDates(d("2026-09-24"));

        assertThat(dates).first().isEqualTo(d("2026-09-16"));
        assertThat(dates).last().isEqualTo(d("2026-09-24"));
    }
}
