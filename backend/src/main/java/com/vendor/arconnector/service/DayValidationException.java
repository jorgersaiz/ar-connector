package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.ValidationProblem;

import java.util.List;
import java.util.stream.Collectors;

/** Un día introducido por un usuario o un CSV no pasa las validaciones del documento. */
public class DayValidationException extends RuntimeException {

    private final List<ValidationProblem> problems;

    public DayValidationException(List<ValidationProblem> problems) {
        super(problems.size() + " problema(s) de validación: "
                + problems.stream().map(ValidationProblem::toString).collect(Collectors.joining(" | ")));
        this.problems = List.copyOf(problems);
    }

    public List<ValidationProblem> getProblems() {
        return problems;
    }
}
