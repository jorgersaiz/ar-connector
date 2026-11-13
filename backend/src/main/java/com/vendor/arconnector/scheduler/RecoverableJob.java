package com.vendor.arconnector.scheduler;

/** Proceso programado cuya ejecución se puede recuperar si no se hizo a su hora. */
public interface RecoverableJob {

    /** Identificador estable; es la clave en la tabla job_run. */
    String name();

    /** Descripción para el panel. */
    String description();

    /** Expresión cron con la que está programado (en la zona horaria de la aplicación). */
    String cron();

    /** El trabajo en sí. Controla sus propios errores. */
    void execute();

    /** true si una ejecución perdida de un día anterior ya no tiene sentido recuperarla. */
    default boolean catchUpOnlySameDay() {
        return false;
    }
}
