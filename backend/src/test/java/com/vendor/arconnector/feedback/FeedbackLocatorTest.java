package com.vendor.arconnector.feedback;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No está claro si el feedback se pide con la fecha de envío o con la de
 * procesamiento: se prueban las dos y se acepta el fichero que corresponde a
 * lo enviado.
 */
class FeedbackLocatorTest {

    private static final String HEADER = "date;person_id;task_level_1_code;task_level_2_code;task_level_3_code;"
            + "task_level_4_code;task_level_5_code;sda_project_id;jira_issue_id;non_sda_project;technology_detail;time";

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    /** Fichero con una línea de 480 minutos por cada día indicado, para 60 personas. */
    private static byte[] file(boolean feedback, String... days) {
        StringBuilder sb = new StringBuilder(HEADER).append(feedback ? ";reason" : "").append("\r\n");
        for (String day : days) {
            for (int p = 0; p < 60; p++) {
                String hash = "abc" + p;
                sb.append(day).append(';').append(feedback ? hash.toUpperCase() : hash)
                        .append(";03;03.01;03.01;03.01;03.01;;;;;480").append(feedback ? ";" : "").append("\r\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Optional<FeedbackLocator.Found> locate(String submission, String today, Map<LocalDate, byte[]> published,
                                                          byte[] sent, List<LocalDate> asked) {
        return FeedbackLocator.locate(d(submission), d(today),
                date -> {
                    asked.add(date);
                    return Optional.ofNullable(published.get(date));
                },
                ActivityRowKeys::of, ActivityRowKeys.of(sent));
    }

    @Test
    void siSePublicaConLaFechaDeProcesamientoSeEncuentraEnElDiaSiguiente() {
        // Caso del ejemplo de BBVA: enviado el 24, feedback con fecha 25
        byte[] sent24 = file(false, "2026-09-22", "2026-09-23", "2026-09-24");
        Map<LocalDate, byte[]> published = new HashMap<>();
        published.put(d("2026-09-24"), file(true, "2026-09-22", "2026-09-23"));                  // feedback del envío del 23
        published.put(d("2026-09-25"), file(true, "2026-09-22", "2026-09-23", "2026-09-24"));    // feedback del envío del 24

        Optional<FeedbackLocator.Found> found = locate("2026-09-24", "2026-09-25", published, sent24, new ArrayList<>());

        assertThat(found).isPresent();
        assertThat(found.get().requestedDate()).isEqualTo(d("2026-09-25"));
    }

    @Test
    void siSePublicaConLaFechaDeEnvioSeEncuentraALaPrimera() {
        byte[] sent24 = file(false, "2026-09-22", "2026-09-23", "2026-09-24");
        Map<LocalDate, byte[]> published = Map.of(d("2026-09-24"), file(true, "2026-09-22", "2026-09-23", "2026-09-24"));
        List<LocalDate> asked = new ArrayList<>();

        Optional<FeedbackLocator.Found> found = locate("2026-09-24", "2026-09-25", published, sent24, asked);

        assertThat(found.get().requestedDate()).isEqualTo(d("2026-09-24"));
        assertThat(asked).containsExactly(d("2026-09-24"));
    }

    @Test
    void elFeedbackDeOtroEnvioNoSeAceptaNunca() {
        byte[] sent24 = file(false, "2026-09-22", "2026-09-23", "2026-09-24");
        Map<LocalDate, byte[]> published = Map.of(d("2026-09-24"), file(true, "2026-09-22", "2026-09-23"));

        assertThat(locate("2026-09-24", "2026-09-25", published, sent24, new ArrayList<>())).isEmpty();
    }

    @Test
    void unEnvioDelViernesSeBuscaHastaPasadoElFinDeSemanaPeroNoEnElFuturo() {
        List<LocalDate> asked = new ArrayList<>();
        locate("2026-09-25", "2026-09-28", Map.of(), file(false, "2026-09-25"), asked);

        assertThat(asked).containsExactly(d("2026-09-25"), d("2026-09-26"), d("2026-09-27"), d("2026-09-28"));
    }

    @Test
    void lasClavesNoDistinguenMayusculasEnElHashNiDependenDelOrden() {
        Set<String> sent = ActivityRowKeys.of((HEADER + "\n2026-09-24;abcdef;03;03.01;03.01;03.01;03.01;;;;;480\n"
                + "2026-09-23;abcdef;;;;;;;;;;\n").getBytes(StandardCharsets.UTF_8));
        Set<String> feedback = ActivityRowKeys.of((HEADER + ";reason\r\n2026-09-23;ABCDEF;;;;;;;;;;;APV_01\r\n"
                + "2026-09-24;ABCDEF;03;03.01;03.01;03.01;03.01;;;;;480;\r\n").getBytes(StandardCharsets.UTF_8));

        assertThat(feedback).isEqualTo(sent);
        assertThat(FeedbackLocator.corresponds(feedback, sent)).isTrue();
    }
}
