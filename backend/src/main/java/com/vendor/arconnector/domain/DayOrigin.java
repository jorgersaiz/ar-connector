package com.vendor.arconnector.domain;

/** De dónde salió un día de actividad, para trazabilidad. */
public enum DayOrigin {
    /** Generado automáticamente a partir de la plantilla por defecto de la persona o del calendario. */
    TEMPLATE,
    /** Introducido o editado a mano en el panel. */
    MANUAL,
    /** Importado desde un fichero CSV. */
    CSV_IMPORT
}
