package com.vendor.arconnector.activities;

import java.util.List;
import java.util.stream.Collectors;

/** El fichero no se ha generado porque hay filas que Activity Report rechazaría o ignoraría. */
public class ActivitiesFileValidationException extends RuntimeException {

    private final List<ValidationProblem> problems;

    public ActivitiesFileValidationException(List<ValidationProblem> problems) {
        super(problems.size() + " problema(s) en el fichero de imputaciones:\n"
                + problems.stream().map(ValidationProblem::toString).collect(Collectors.joining("\n")));
        this.problems = List.copyOf(problems);
    }

    public List<ValidationProblem> getProblems() {
        return problems;
    }

    public boolean hasCode(String code) {
        return problems.stream().anyMatch(p -> p.code().equals(code));
    }
}
