package com.vendor.arconnector.imports;

import com.vendor.arconnector.activities.LineSubmission;
import com.vendor.arconnector.catalog.SemicolonCsv;
import com.vendor.arconnector.domain.DayKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DayImportParserTest {

    private static final String HEADER = "uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note\n";

    @Test
    void agrupaLasFilasPorPersonaYDia() {
        List<ImportedDay> days = DayImportParser.parse(SemicolonCsv.parse(HEADER
                + "xe12345;2026-09-15;;01.01.01;SDATOOL-37040;;;420;\n"
                + "XE12345;2026-09-15;WORKED;03.01;;;;60;Formación\n"
                + "XE12345;2026-09-16;NON_WORKING;;;;;;Vacaciones\n"
                + "XE99999;2026-09-15;;02.01;;;Soporte interno;480;\n"));

        assertThat(days).hasSize(3);

        ImportedDay d15 = days.get(0);
        assertThat(d15.uid()).isEqualTo("XE12345");   // el uid se normaliza a mayúsculas
        assertThat(d15.date()).isEqualTo(LocalDate.parse("2026-09-15"));
        assertThat(d15.kind()).isEqualTo(DayKind.WORKED);
        assertThat(d15.note()).isEqualTo("Formación");
        assertThat(d15.lines()).containsExactly(
                LineSubmission.sda("01.01.01", "SDATOOL-37040", 420),
                LineSubmission.of("03.01", 60));

        ImportedDay d16 = days.get(1);
        assertThat(d16.kind()).isEqualTo(DayKind.NON_WORKING);
        assertThat(d16.note()).isEqualTo("Vacaciones");
        assertThat(d16.lines()).isEmpty();

        assertThat(days.get(2).lines().get(0).nonSdaProject()).isEqualTo("Soporte interno");
    }

    @Test
    void cabeceraIncompletaSeRechazaConLaCabeceraEsperada() {
        assertThatThrownBy(() -> DayImportParser.parse(SemicolonCsv.parse("uid;date;hours\nXE1;2026-09-15;8\n")))
                .isInstanceOf(DayImportException.class)
                .hasMessageContaining("task_type_code")
                .hasMessageContaining("Esperada");
    }

    @Test
    void seAcumulanLosErroresDeFila() {
        assertThatThrownBy(() -> DayImportParser.parse(SemicolonCsv.parse(HEADER
                + "XE1;ayer;;03.01;;;;60;\n"                          // fecha
                + "XE1;2026-09-15;FESTIVO;;;;;;\n"                    // kind
                + "XE1;2026-09-16;NON_WORKING;03.01;;;;60;\n"         // no imputable con actividad
                + "XE1;2026-09-17;;03.01;;;;ocho;\n"                  // minutos
                + "XE1;2026-09-18;;;;;;60;\n")))                      // sin actividad
                .isInstanceOf(DayImportException.class)
                .hasMessageContaining("5 error(es)")
                .hasMessageContaining("línea 2")
                .hasMessageContaining("línea 6");
    }

    @Test
    void unDiaNoPuedeSerWorkedYNonWorkingALaVez() {
        assertThatThrownBy(() -> DayImportParser.parse(SemicolonCsv.parse(HEADER
                + "XE1;2026-09-15;;03.01;;;;60;\n"
                + "XE1;2026-09-15;NON_WORKING;;;;;;\n")))
                .isInstanceOf(DayImportException.class)
                .hasMessageContaining("WORKED y como NON_WORKING");
    }
}
