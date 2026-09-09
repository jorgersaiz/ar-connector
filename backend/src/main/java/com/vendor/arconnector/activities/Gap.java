package com.vendor.arconnector.activities;

import java.time.LocalDate;

/**
 * Día laborable de una persona para el que no hay actividad registrada ni
 * plantilla por defecto aplicable. No se envía nada; es lo que hay que
 * reclamar antes del corte.
 */
public record Gap(String uid, LocalDate date, LocalDate lastSubmissionDate) {
}
