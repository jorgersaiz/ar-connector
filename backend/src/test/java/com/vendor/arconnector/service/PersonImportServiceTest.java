package com.vendor.arconnector.service;

import com.vendor.arconnector.config.ActivityReportProperties;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.RegistrationStatus;
import com.vendor.arconnector.imports.CsvImportException;
import com.vendor.arconnector.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class PersonImportServiceTest {

    private static final String HEADER = "uid;display_name;email;start_date;end_date;geography;gps_code;needs_registration\n";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T09:00:00Z"), ZoneId.of("Europe/Madrid"));

    private PersonRepository repository;
    private PersonImportService service;
    private final List<Person> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(PersonRepository.class);
        when(repository.findByUidIn(anyCollection())).thenAnswer(inv -> {
            var uids = (java.util.Collection<?>) inv.getArgument(0);
            return stored.stream().filter(p -> uids.contains(p.getUid())).toList();
        });
        ActivityReportProperties props = new ActivityReportProperties("https://dev-gag.bbva.com/timereport/api/vendors", "",
                "0000417143", "ES", ActivityReportProperties.ClientMode.HTTP, null, null, null, false, null, null);
        service = new PersonImportService(repository, props, CLOCK);
    }

    private PersonImportService.Summary run(String csv, boolean dryRun) {
        return service.importCsv((HEADER + csv).getBytes(StandardCharsets.UTF_8), dryRun);
    }

    @SuppressWarnings("unchecked")
    private List<Person> saved() {
        ArgumentCaptor<List<Person>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static Person existing(String uid, String name, String email) {
        Person p = new Person(uid, email, name, "0000417143", "ES", LocalDate.parse("2026-01-01"));
        p.setId((long) uid.hashCode());
        return p;
    }

    @Test
    void creaLasPersonasNuevasConElGpsYLaGeografiaDeLaEmpresa() {
        PersonImportService.Summary s = run("XE1;Ana López;ana@arktic.com;2026-11-01;;;;si\n", false);

        assertThat(s.created()).isEqualTo(1);
        assertThat(s.registrationsRequested()).isEqualTo(1);
        Person ana = saved().get(0);
        assertThat(ana.getUid()).isEqualTo("XE1");
        assertThat(ana.getGpsCode()).isEqualTo("0000417143");
        assertThat(ana.getGeography()).isEqualTo("ES");
        assertThat(ana.getRegistrationStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(s.rows().get(0).action()).isEqualTo(PersonImportService.Action.CREAR);
    }

    @Test
    void actualizaLasQueExistenYDiceQueCambia() {
        stored.add(existing("XE1", "Ana", "ana@arktic.com"));

        PersonImportService.Summary s = run("XE1;Ana López;ana.lopez@arktic.com;2026-01-01;2026-12-31;;;\n", false);

        assertThat(s.updated()).isEqualTo(1);
        assertThat(s.rows().get(0).changes()).containsExactly(
                "nombre: Ana → Ana López", "email: ana@arktic.com → ana.lopez@arktic.com", "baja: — → 2026-12-31");
        Person ana = saved().get(0);
        assertThat(ana.getEmail()).isEqualTo("ana.lopez@arktic.com");
        assertThat(ana.getEndDate()).isEqualTo(LocalDate.parse("2026-12-31"));
    }

    @Test
    void reimportarElMismoFicheroNoCambiaNadaNiBorraUnaBajaHechaEnElPanel() {
        Person ana = existing("XE1", "Ana", "ana@arktic.com");
        ana.setEndDate(LocalDate.parse("2026-10-31"));   // baja dada desde el panel
        stored.add(ana);

        PersonImportService.Summary s = run("XE1;Ana;ana@arktic.com;2026-01-01;;;;\n", false);

        assertThat(s.unchanged()).isEqualTo(1);
        assertThat(s.updated()).isZero();
        assertThat(ana.getEndDate()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(saved()).isEmpty();
    }

    // Que la comprobación no deje cambios en la base de datos (Hibernate guarda solo las entidades
    // modificadas) se verifica de punta a punta contra PostgreSQL; aquí se comprueba que no llama a save.
    @Test
    void laSimulacionNoGuardaNada() {
        stored.add(existing("XE1", "Ana", "ana@arktic.com"));

        PersonImportService.Summary s = run("XE1;Ana López;ana@arktic.com;2026-01-01;;;;\nXE2;Bruno;bruno@arktic.com;2026-11-01;;;;\n", true);

        assertThat(s.dryRun()).isTrue();
        assertThat(s.created()).isEqualTo(1);
        assertThat(s.updated()).isEqualTo(1);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void noSeTocaUnAltaQueYaSeEnvioABbva() {
        Person ana = existing("XE1", "Ana", "ana@arktic.com");
        ana.setRegistrationStatus(RegistrationStatus.SENT);
        stored.add(ana);

        PersonImportService.Summary s = run("XE1;Ana;ana@arktic.com;2026-01-01;;;;si\n", false);

        assertThat(ana.getRegistrationStatus()).isEqualTo(RegistrationStatus.SENT);
        assertThat(s.unchanged()).isEqualTo(1);
        assertThat(s.registrationsRequested()).isZero();
        assertThat(s.rows().get(0).changes()).singleElement().asString().contains("ya se envió");
    }

    @Test
    void siUnaFilaFallaNoSeGuardaNinguna() {
        assertThatThrownBy(() -> run("XE1;Ana;ana@arktic.com;2026-11-01;;;;\nXE2;Bruno;sin-arroba;2026-11-01;;;;\n", false))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("línea 3");
        verify(repository, never()).saveAll(any());
    }
}
