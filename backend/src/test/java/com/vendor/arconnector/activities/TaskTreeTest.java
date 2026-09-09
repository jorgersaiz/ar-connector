package com.vendor.arconnector.activities;

import com.vendor.arconnector.activities.TaskTree.Node;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Se deberán informar los cinco niveles de actividad, incluyendo todos los
 * niveles intermedios hasta la actividad imputada por el usuario. El resto de
 * niveles hasta llegar a los cinco, repetirá el último nivel."
 */
class TaskTreeTest {

    private final TaskTree tree = TaskTree.of(List.of(
            new Node("01", null, false, null, true, false, false, false),
            Node.branch("01.01", "01"),
            Node.leaf("01.01.01", "01.01"),
            Node.branch("05", null),
            Node.branch("05.01", "05"),
            Node.branch("05.01.01", "05.01"),
            Node.branch("05.01.01.01", "05.01.01"),
            Node.leaf("05.01.01.01.01", "05.01.01.01"),
            Node.leaf("09", null),
            new Node("03.02", null, true, LocalDate.parse("2026-03-10"), false, false, false, false)
    ));

    @Test
    void repiteElUltimoNivelHastaCompletarCinco() {
        assertThat(tree.fiveLevels("01.01.01"))
                .containsExactly("01", "01.01", "01.01.01", "01.01.01", "01.01.01");
        assertThat(tree.fiveLevels("09"))
                .containsExactly("09", "09", "09", "09", "09");
    }

    @Test
    void cincoNivelesRealesSeInformanTalCual() {
        assertThat(tree.fiveLevels("05.01.01.01.01"))
                .containsExactly("05", "05.01", "05.01.01", "05.01.01.01", "05.01.01.01.01");
    }

    @Test
    void nodoDesconocidoLanzaError() {
        assertThatThrownBy(() -> tree.pathTo("99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void padreDesconocidoLanzaError() {
        TaskTree broken = TaskTree.of(List.of(Node.leaf("X.Y", "X")));
        assertThatThrownBy(() -> broken.pathTo("X.Y")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masDeCincoNivelesLanzaError() {
        TaskTree deep = TaskTree.of(List.of(
                Node.branch("A", null), Node.branch("B", "A"), Node.branch("C", "B"),
                Node.branch("D", "C"), Node.branch("E", "D"), Node.leaf("F", "E")));
        assertThatThrownBy(() -> deep.pathTo("F")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cicloLanzaError() {
        TaskTree cyclic = TaskTree.of(List.of(Node.branch("A", "B"), Node.branch("B", "A")));
        assertThatThrownBy(() -> cyclic.pathTo("A")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nodoDuplicadoLanzaError() {
        assertThatThrownBy(() -> TaskTree.of(List.of(Node.leaf("A", null), Node.leaf("A", null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledDateEsImputableHastaEseDiaIncluido() {
        Node node = tree.find("03.02").orElseThrow();
        assertThat(node.isEnabledOn(LocalDate.parse("2026-03-10"))).isTrue();
        assertThat(node.isEnabledOn(LocalDate.parse("2026-03-11"))).isFalse();
    }

    @Test
    void laExigenciaDeProyectoSdaSeHeredaDelAntecesor() {
        // Solo "01" tiene requires_sda_project = true; sus descendientes lo heredan
        assertThat(tree.effectiveRequirementsFor("01.01.01").sdaProject()).isTrue();
        assertThat(tree.effectiveRequirementsFor("09").sdaProject()).isFalse();
    }
}
