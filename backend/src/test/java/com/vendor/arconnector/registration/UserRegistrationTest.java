package com.vendor.arconnector.registration;

import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.RegistrationStatus;
import com.vendor.arconnector.registration.UserRegistrationFeedbackParser.Row;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fichero "REGISTRO DE USUARIOS" y su feedback (APV_UR_xx). */
class UserRegistrationTest {

    private static final LocalDate SENT = LocalDate.parse("2026-09-16");

    private static Person sentPerson(String uid, String startDate) {
        Person p = new Person(uid, uid.toLowerCase() + "@arktic.com", uid, "GPS001", "ES", LocalDate.parse(startDate));
        p.setRegistrationStatus(RegistrationStatus.SENT);
        p.setRegistrationSentDate(SENT);
        return p;
    }

    @Test
    void elFicheroTieneLaColumnaUidsSinDuplicadosNiVacios() {
        UserRegistrationFile.Built built = UserRegistrationFile.build(List.of("xe1", "XE2", " XE1 ", "", "XE3"));

        assertThat(new String(built.content(), StandardCharsets.UTF_8)).isEqualTo("uids\nXE1\nXE2\nXE3\n");
        assertThat(built.count()).isEqualTo(3);
    }

    @Test
    void elFeedbackSeLeeConUidsUidYReason() {
        List<Row> rows = UserRegistrationFeedbackParser.parse(SemicolonCsv.parse(
                "uids;uid;reason\nxe1;abc;\nXE2;def;APV_UR_05\n"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).uids()).isEqualTo("XE1");
        assertThat(rows.get(0).isRejected()).isFalse();
        assertThat(rows.get(1).reason()).isEqualTo("APV_UR_05");
    }

    @Test
    void elFeedbackRealSoloTraeElHashEnMayusculasYElMotivo() {
        // Formato del fichero de ejemplo de BBVA: users_VENDORX_20260925_feedback.csv
        Person p = sentPerson("XE99002", "2026-09-01");
        String hash = p.personId().toUpperCase();

        List<Row> rows = UserRegistrationFeedbackParser.parse(SemicolonCsv.parse(
                "uid;reason\r\n" + hash + ";\r\nE7713342BE08A3C82EBB50BA3E0972CAF61BD36E096AA0E7AB76E92EEFFEE31B;APV_UR_01\r\n"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).uid()).isEqualTo(hash);
        assertThat(rows.get(0).uids()).isEmpty();

        UserRegistrationFeedbackApplier.Result r = UserRegistrationFeedbackApplier.apply(rows, List.of(p), SENT);
        assertThat(r.registered()).isEqualTo(1);
        assertThat(r.unmatched()).isEqualTo(1);
        assertThat(p.getRegistrationStatus()).isEqualTo(RegistrationStatus.REGISTERED);
    }

    @Test
    void sinIdentificadorDeUsuarioNoEsFeedback() {
        assertThatThrownBy(() -> UserRegistrationFeedbackParser.parse(SemicolonCsv.parse("nombre;reason\nAna;\n")))
                .isInstanceOf(CatalogParseException.class).hasMessageContaining("uid");
    }

    @Test
    void sinColumnaReasonNoEsFeedback() {
        assertThatThrownBy(() -> UserRegistrationFeedbackParser.parse(SemicolonCsv.parse("uids\nXE1\n")))
                .isInstanceOf(CatalogParseException.class);
    }

    @Test
    void registradaAdelantaLaFechaDeAltaAlDiaSiguienteDelEnvio() {
        Person early = sentPerson("XE1", "2026-09-01");      // pedía imputar desde el 1, pero AR la da de alta el 17
        Person later = sentPerson("XE2", "2026-10-01");      // ya tenía una fecha posterior: se respeta

        UserRegistrationFeedbackApplier.Result r = UserRegistrationFeedbackApplier.apply(List.of(
                new Row(2, "XE1", "", null),
                new Row(3, "XE2", "", null)
        ), List.of(early, later), SENT);

        assertThat(r.registered()).isEqualTo(2);
        assertThat(early.getRegistrationStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(early.getStartDate()).isEqualTo(LocalDate.parse("2026-09-17"));
        assertThat(later.getStartDate()).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    @Test
    void rechazadaGuardaElMotivoYCasaTambienPorHash() {
        Person p = sentPerson("XE1", "2026-09-01");

        UserRegistrationFeedbackApplier.Result r = UserRegistrationFeedbackApplier.apply(List.of(
                new Row(2, "", p.personId(), "APV_UR_02"),
                new Row(3, "XE9", "", "APV_UR_01")
        ), List.of(p), SENT);

        assertThat(r.matched()).isEqualTo(1);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(r.unmatched()).isEqualTo(1);
        assertThat(p.getRegistrationStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(p.getRegistrationReason()).isEqualTo("APV_UR_02");
        assertThat(p.getStartDate()).isEqualTo(LocalDate.parse("2026-09-01"));
    }
}
