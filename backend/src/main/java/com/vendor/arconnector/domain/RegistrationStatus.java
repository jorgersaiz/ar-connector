package com.vendor.arconnector.domain;

/**
 * Estado del alta de la persona en Activity Report vía API.
 *
 * NOT_REQUIRED: ya tenía acceso (o se gestionó por otra vía); no se envía nada.
 * PENDING:      pendiente de incluir en el próximo fichero de registro.
 * SENT:         enviada; el feedback llega al día siguiente.
 * REGISTERED:   aceptada; su fecha de inicio en AR es el día siguiente al envío.
 * REJECTED:     rechazada con un código APV_UR_xx; requiere corrección manual y volver a solicitar.
 */
public enum RegistrationStatus {
    NOT_REQUIRED,
    PENDING,
    SENT,
    REGISTERED,
    REJECTED
}
