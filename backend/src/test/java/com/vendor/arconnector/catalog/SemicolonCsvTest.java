package com.vendor.arconnector.catalog;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemicolonCsvTest {

    @Test
    void leeCabeceraYFilasSeparadasPorPuntoYComa() {
        CsvTable t = SemicolonCsv.parse("a;b;c\n1;2;3\n4;5;6\n");

        assertThat(t.headers()).containsExactly("a", "b", "c");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(0).text("b")).isEqualTo("2");
        assertThat(t.rows().get(1).text("c")).isEqualTo("6");
        assertThat(t.rows().get(1).lineNumber()).isEqualTo(3);
    }

    @Test
    void aceptaBomYFinalesDeLineaWindows() {
        byte[] bytes = ("﻿a;b\r\n1;2\r\n").getBytes(StandardCharsets.UTF_8);
        CsvTable t = SemicolonCsv.parse(bytes);

        assertThat(t.headers()).containsExactly("a", "b");
        assertThat(t.rows().get(0).text("a")).isEqualTo("1");
        assertThat(t.rows().get(0).text("b")).isEqualTo("2");
    }

    @Test
    void camposEntrecomilladosPuedenContenerPuntoYComaYComillasEscapadas() {
        CsvTable t = SemicolonCsv.parse("id;desc\n1;\"Pruebas; revisión \"\"final\"\"\"\n");

        assertThat(t.rows().get(0).text("desc")).isEqualTo("Pruebas; revisión \"final\"");
    }

    @Test
    void lasColumnasSeBuscanPorNombreSinDistinguirMayusculas() {
        CsvTable t = SemicolonCsv.parse("Task_Type_ID;Order\n01;3\n");

        assertThat(t.hasColumn("task_type_id")).isTrue();
        assertThat(t.rows().get(0).text("TASK_TYPE_ID")).isEqualTo("01");
        assertThat(t.rows().get(0).requiredInt("order")).isEqualTo(3);
    }

    @Test
    void ignoraLineasEnBlancoYToleraColumnasFinalesAusentes() {
        CsvTable t = SemicolonCsv.parse("a;b;c\n1;2\n\n   \n3;4;5\n");

        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(0).text("c")).isEmpty();
        assertThat(t.rows().get(0).optionalText("c")).isNull();
    }

    @Test
    void rechazaFilasConMasColumnasQueLaCabecera() {
        assertThatThrownBy(() -> SemicolonCsv.parse("a;b\n1;2;3\n"))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("línea 2");
    }

    @Test
    void rechazaFicheroVacioOComillasSinCerrar() {
        assertThatThrownBy(() -> SemicolonCsv.parse("")).isInstanceOf(CatalogParseException.class);
        assertThatThrownBy(() -> SemicolonCsv.parse("a;b\n1;\"abierto\n")).isInstanceOf(CatalogParseException.class);
    }

    @Test
    void detectaColumnasObligatoriasAusentes() {
        CsvTable t = SemicolonCsv.parse("a;b\n1;2\n");

        assertThat(t.missingColumns(java.util.List.of("a", "z"))).containsExactly("z");
        assertThatThrownBy(() -> t.requireColumns("a", "z"))
                .isInstanceOf(CatalogParseException.class)
                .hasMessageContaining("faltan columnas")
                .hasMessageContaining("z");
    }

    @Test
    void conversionesTipadasConErroresQueIndicanLaLinea() {
        // la última fila tiene vacías las dos primeras columnas (una fila totalmente vacía se ignora)
        CsvTable t = SemicolonCsv.parse("d;b;n\n2026-03-10;TRUE;7\nmal;quizás;x\n;;9\n");
        CsvRow ok = t.rows().get(0);
        CsvRow bad = t.rows().get(1);
        CsvRow empty = t.rows().get(2);

        assertThat(ok.optionalDate("d")).isEqualTo(LocalDate.parse("2026-03-10"));
        assertThat(ok.bool("b")).isTrue();
        assertThat(ok.requiredInt("n")).isEqualTo(7);

        assertThat(empty.optionalDate("d")).isNull();
        assertThat(empty.bool("b")).isFalse();
        assertThat(empty.optionalInt("n")).isEqualTo(9);
        assertThat(empty.optionalText("d")).isNull();

        assertThatThrownBy(() -> bad.optionalDate("d")).isInstanceOf(CatalogRowException.class).hasMessageContaining("línea 3");
        assertThatThrownBy(() -> bad.bool("b")).isInstanceOf(CatalogRowException.class).hasMessageContaining("true/false");
        assertThatThrownBy(() -> bad.requiredInt("n")).isInstanceOf(CatalogRowException.class).hasMessageContaining("entero");
        assertThatThrownBy(() -> empty.requiredText("d")).isInstanceOf(CatalogRowException.class).hasMessageContaining("obligatoria");
    }
}
