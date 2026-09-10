-- =============================================================================
-- Avisos enviados (o solo registrados, si no hay correo configurado):
-- recordatorios de huecos y rechazos a las personas y resumen a los managers.
-- Sirve de histórico para el panel y para no repetir el mismo aviso el mismo día.
-- =============================================================================
CREATE TABLE notification (
    id               BIGSERIAL PRIMARY KEY,
    sent_at          TIMESTAMP    NOT NULL,
    channel          VARCHAR(16)  NOT NULL,        -- LOG | EMAIL
    kind             VARCHAR(32)  NOT NULL,        -- GAPS_REMINDER | REJECTIONS | MANAGER_SUMMARY
    recipient_email  VARCHAR(255) NOT NULL,
    person_id        BIGINT       REFERENCES person (id),
    subject          VARCHAR(255) NOT NULL,
    body             TEXT         NOT NULL,
    deadline         DATE
);

CREATE INDEX idx_notification_sent_at ON notification (sent_at);
CREATE INDEX idx_notification_recipient_kind ON notification (recipient_email, kind, sent_at);
