package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.TaskType;

import java.time.LocalDate;
import java.util.*;

/**
 * Árbol de actividades en memoria, construido a partir del catálogo
 * "task_types". Resuelve el camino desde la raíz hasta un nodo hoja para
 * rellenar las cinco columnas task_level_N_code del fichero de imputaciones.
 *
 * Reglas del documento que aplica:
 *  - Se informan los cinco niveles, incluyendo los intermedios; los niveles que
 *    falten hasta cinco repiten el último.
 *  - Solo se puede imputar a nodos con can_be_selected = true (hojas).
 *  - Un nodo con disabled_date es imputable hasta ese día incluido.
 */
public final class TaskTree {

    public static final int LEVELS = 5;

    /** Vista inmutable de un nodo del árbol; independiente de JPA para poder testear sin BD. */
    public record Node(
            String code,
            String parentCode,
            boolean canBeSelected,
            LocalDate disabledDate,
            boolean requiresSdaProject,
            boolean requiresJiraIssue,
            boolean requiresNonSdaProject,
            boolean requiresTechnologyDetail,
            boolean requiresEvolutionarySda,
            boolean requiresEvolutionaryJiraIssue,
            boolean requiresConceptualizationTime
    ) {
        /** Constructor sin las exigencias de evolutivo / conceptualization time (a false). */
        public Node(String code, String parentCode, boolean canBeSelected, LocalDate disabledDate,
                    boolean requiresSdaProject, boolean requiresJiraIssue, boolean requiresNonSdaProject,
                    boolean requiresTechnologyDetail) {
            this(code, parentCode, canBeSelected, disabledDate, requiresSdaProject, requiresJiraIssue,
                    requiresNonSdaProject, requiresTechnologyDetail, false, false, false);
        }

        public Node {
            Objects.requireNonNull(code, "code");
            if (parentCode != null && parentCode.isBlank()) {
                parentCode = null;
            }
        }

        public static Node leaf(String code, String parentCode) {
            return new Node(code, parentCode, true, null, false, false, false, false);
        }

        public static Node branch(String code, String parentCode) {
            return new Node(code, parentCode, false, null, false, false, false, false);
        }

        /** true si el nodo sigue siendo imputable en la fecha dada (disabled_date incluido). */
        public boolean isEnabledOn(LocalDate date) {
            return disabledDate == null || !date.isAfter(disabledDate);
        }
    }

    private final Map<String, Node> nodes;

    private TaskTree(Map<String, Node> nodes) {
        this.nodes = Map.copyOf(nodes);
    }

    public static TaskTree of(Collection<Node> nodes) {
        Map<String, Node> byCode = new LinkedHashMap<>();
        for (Node n : nodes) {
            if (byCode.put(n.code(), n) != null) {
                throw new IllegalArgumentException("Nodo duplicado en el árbol: " + n.code());
            }
        }
        return new TaskTree(byCode);
    }

    public static TaskTree fromCatalog(Collection<TaskType> catalog) {
        List<Node> list = catalog.stream().map(t -> new Node(
                t.getTaskTypeId(),
                t.getTaskTypeParentId(),
                t.isCanBeSelected(),
                t.getDisabledDate(),
                t.isRequiresSdaProject(),
                t.isRequiresJiraIssue(),
                t.isRequiresNonSdaProject(),
                t.isRequiresTechnologyDetail(),
                t.isRequiresEvolutionarySda(),
                t.isRequiresEvolutionaryJiraIssue(),
                t.isRequiresConceptualizationTime()
        )).toList();
        return of(list);
    }

    public Optional<Node> find(String code) {
        return Optional.ofNullable(nodes.get(code));
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Camino desde la raíz hasta el nodo (raíz primero). Lanza excepción si el
     * nodo o alguno de sus antecesores no existe, si hay ciclos o si la
     * profundidad supera los cinco niveles.
     */
    public List<String> pathTo(String code) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        String current = code;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalStateException("Ciclo en el árbol de actividades en el nodo " + current);
            }
            Node node = nodes.get(current);
            if (node == null) {
                throw new IllegalArgumentException("Nodo desconocido en el árbol de actividades: " + current);
            }
            path.addFirst(current);
            if (path.size() > LEVELS) {
                throw new IllegalStateException("El nodo " + code + " tiene más de " + LEVELS + " niveles");
            }
            current = node.parentCode();
        }
        return List.copyOf(path);
    }

    /**
     * Qué exige una actividad en cuanto a proyecto SDA / issue de Jira /
     * proyecto no SDA, teniendo en cuenta todo su camino desde la raíz.
     *
     * El documento dice que "01" es "la única [actividad] con requires_sda_project
     * a true" y que las actividades que dependen de ella son las de proyectos
     * SDA, así que la exigencia se hereda de los antecesores: basta con que un
     * nodo del camino la tenga a true.
     */
    public record Requirements(boolean sdaProject, boolean jiraIssue, boolean nonSdaProject, boolean technologyDetail,
                               boolean evolutionarySda, boolean evolutionaryJiraIssue, boolean conceptualizationTime) {
        public Requirements(boolean sdaProject, boolean jiraIssue, boolean nonSdaProject, boolean technologyDetail) {
            this(sdaProject, jiraIssue, nonSdaProject, technologyDetail, false, false, false);
        }
    }

    public Requirements effectiveRequirementsFor(String code) {
        boolean sda = false, jira = false, nonSda = false, tech = false, evoSda = false, evoJira = false, concept = false;
        for (String c : pathTo(code)) {
            Node n = nodes.get(c);
            sda |= n.requiresSdaProject();
            jira |= n.requiresJiraIssue();
            nonSda |= n.requiresNonSdaProject();
            tech |= n.requiresTechnologyDetail();
            evoSda |= n.requiresEvolutionarySda();
            evoJira |= n.requiresEvolutionaryJiraIssue();
            concept |= n.requiresConceptualizationTime();
        }
        return new Requirements(sda, jira, nonSda, tech, evoSda, evoJira, concept);
    }

    /** Los cinco códigos task_level_1..5 del fichero, repitiendo el último nivel hasta completar cinco. */
    public List<String> fiveLevels(String code) {
        List<String> path = pathTo(code);
        List<String> levels = new ArrayList<>(path);
        String last = path.get(path.size() - 1);
        while (levels.size() < LEVELS) {
            levels.add(last);
        }
        return List.copyOf(levels);
    }
}
