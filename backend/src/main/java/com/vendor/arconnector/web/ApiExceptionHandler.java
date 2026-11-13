package com.vendor.arconnector.web;

import com.vendor.arconnector.activities.ValidationProblem;
import com.vendor.arconnector.catalog.CatalogParseException;
import com.vendor.arconnector.imports.CsvImportException;
import com.vendor.arconnector.service.DayValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Respuestas de error uniformes para el panel: mensaje + lista de detalles. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String message, List<String> errors, List<ValidationProblem> problems) {
    }

    @ExceptionHandler(DayValidationException.class)
    public ResponseEntity<ErrorResponse> dayValidation(DayValidationException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Los datos no pasan las validaciones de Activity Report", List.of(), e.getProblems()));
    }

    @ExceptionHandler(CsvImportException.class)
    public ResponseEntity<ErrorResponse> importError(CsvImportException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("El CSV no se ha podido importar", e.getErrors(), List.of()));
    }

    @ExceptionHandler(CatalogParseException.class)
    public ResponseEntity<ErrorResponse> parseError(CatalogParseException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Fichero mal formado", e.getErrors(), List.of()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage(), List.of(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse("Datos inválidos", errors, List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Conflicto con datos existentes (¿duplicado?)", List.of(), List.of()));
    }
}
