package com.vendor.arconnector.imports;

import com.vendor.arconnector.catalog.SemicolonCsv;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonImportParserTest {

    private static final String HEADER = PersonImportParser.HEADER + "\n";
    private static final PersonImportParser.Defaults ARKTIC = new PersonImportParser.Defaults("ES", "0000417143");
    private static final PersonImportParser.Defaults NONE = new PersonImportParser.Defaults(null, null);

    private static List<ImportedPerson> parse(String csv, PersonImportParser.Defaults defaults) {
        return PersonImportParser.parse(SemicolonCsv.parse(csv.getBytes(StandardCharsets.UTF_8)), defaults);
    }

    private static List<String> errors(String csv, PersonImportParser.Defaults defaults) {
        try {
            parse(csv, defaults);
        } catch (CsvImportException e) {
            return e.getErrors();
        }
        throw new AssertionError("se esperaba un error");
    }

    @Test
    void filaCompletaYFilaMinimaConLosValoresDeLaEmpresa() {
        List<ImportedPerson> persons = parse(HEADER
                + "xe12345;Ana López;ana.lopez@arktic.com;2026-11-01;;;;no\n"
                + "XE67890;Bruno Pérez;bruno@arktic.com;15/11/2026;31/12/2026;PT;0000999999;si\n", ARKTIC);

        assertThat(persons).hasSize(2);
        ImportedPerson ana = persons.get(0);
        assertThat(ana.uid()).isEqualTo("XE12345");                 // uid en mayúsculas
        assertThat(ana.displayName()).isEqualTo("Ana López");
        assertThat(ana.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(ana.geography()).isNull();                       // vacío: se usará el de la empresa
        assertThat(ana.gpsCode()).isNull();
        assertThat(ana.needsRegistration()).isFalse();

        ImportedPerson bruno = persons.get(1);
        assertThat(bruno.startDate()).isEqualTo(LocalDate.parse("2026-11-15"));   // formato del Excel español
        assertThat(bruno.endDate()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(bruno.geography()).isEqualTo("PT");
        assertThat(bruno.gpsCode()).isEqualTo("0000999999");
        assertThat(bruno.needsRegistration()).isTrue();
    }

    @Test
    void lasColumnasOpcionalesPuedenNoEstarEnLaCabecera() {
        List<ImportedPerson> persons = parse("uid;display_name;email;start_date\nXE1;Ana;ana@arktic.com;2026-11-01\n", ARKTIC);

        assertThat(persons.get(0).endDate()).isNull();
        assertThat(persons.get(0).needsRegistration()).isNull();
    }

    @Test
    void unCsvDelExcelEspanolSeLeeConSusTildes() {
        // "CSV (delimitado por punto y coma)" del Excel en español se guarda en Windows-1252, no en UTF-8
        byte[] excel = (HEADER + "XE1;José Núñez;jose@arktic.com;01/11/2026;;;;\n").getBytes(Charset.forName("windows-1252"));

        List<ImportedPerson> persons = PersonImportParser.parse(SemicolonCsv.parse(excel), ARKTIC);

        assertThat(persons.get(0).displayName()).isEqualTo("José Núñez");
    }

    @Test
    void todosLosErroresDeUnaVezConSuNumeroDeLinea() {
        List<String> errors = errors(HEADER
                + ";Sin uid;x@arktic.com;2026-11-01;;;;\n"
                + "XE2;Email malo;no-es-un-email;2026-11-01;;;;\n"
                + "XE3;Fechas al revés;c@arktic.com;2026-11-10;2026-11-01;;;\n"
                + "XE4;Fecha mala;d@arktic.com;31/02/2026;;;;\n"
                + "XE5;Alta rara;e@arktic.com;2026-11-01;;;;quizas\n"
                + "XE-6;Uid raro;f@arktic.com;2026-11-01;;;;\n", ARKTIC);

        assertThat(errors).hasSize(6);
        assertThat(errors.get(0)).contains("línea 2").contains("uid");
        assertThat(errors.get(1)).contains("línea 3").contains("email");
        assertThat(errors.get(2)).contains("línea 4").contains("anterior");
        assertThat(errors.get(3)).contains("línea 5").contains("fecha");
        assertThat(errors.get(4)).contains("línea 6").contains("needs_registration");
        assertThat(errors.get(5)).contains("línea 7").contains("XE-6");
    }

    @Test
    void unUidRepetidoEnElFicheroEsUnError() {
        List<String> errors = errors(HEADER
                + "XE1;Ana;ana@arktic.com;2026-11-01;;;;\n"
                + "xe1;Ana otra vez;ana2@arktic.com;2026-11-01;;;;\n", ARKTIC);

        assertThat(errors).singleElement().asString().contains("línea 3").contains("línea 2");
    }

    @Test
    void sinValoresDeEmpresaHayQueInformarGeografiaYGps() {
        List<String> errors = errors(HEADER + "XE1;Ana;ana@arktic.com;2026-11-01;;;;\n", NONE);

        assertThat(errors).singleElement().asString().contains("geography");
        assertThat(parse(HEADER + "XE1;Ana;ana@arktic.com;2026-11-01;;ES;0000417143;\n", NONE)).hasSize(1);
    }

    @Test
    void faltanColumnasObligatoriasEnLaCabecera() {
        assertThatThrownBy(() -> parse("uid;nombre;email\nXE1;Ana;ana@arktic.com\n", ARKTIC))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("display_name")
                .hasMessageContaining("start_date");
    }

    @Test
    void unFicheroSoloConCabeceraEsUnError() {
        assertThat(errors(HEADER, ARKTIC)).singleElement().asString().contains("ninguna persona");
    }
}
