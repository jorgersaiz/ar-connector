package com.vendor.arconnector.domain;

/**
 * Alcance de un festivo del calendario laboral.
 *
 * NATIONAL: festivo que Activity Report ya conoce para esa geografía. No se
 *           envía nada ese día; si se enviase, BBVA lo rechazaría con APV_01
 *           ("la fecha es festivo, fin de semana o fuera del periodo modificable").
 * LOCAL:    festivo local (autonómico, municipal...) que BBVA no conoce. El
 *           documento indica que "los festivos locales tendrán la consideración
 *           de días no imputables y deberán informarse de la misma manera", es
 *           decir, como fila con solo fecha y person_id.
 *
 * TODO: confirmar con los administradores de Activity Report qué festivos
 * consideran ellos "festivo" para cada geografía, para clasificarlos bien.
 */
public enum HolidayScope {
    NATIONAL,
    LOCAL
}
