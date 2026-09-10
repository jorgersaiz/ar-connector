-- =============================================================================
-- Registro de usuarios nuevos en Activity Report (fichero "REGISTRO DE
-- USUARIOS" y su feedback). Solo para personas que nunca han tenido acceso:
-- el registro no reactiva usuarios y los ya registrados se rechazan (APV_UR_05).
-- =============================================================================
ALTER TABLE person ADD COLUMN registration_status    VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED';  -- NOT_REQUIRED | PENDING | SENT | REGISTERED | REJECTED
ALTER TABLE person ADD COLUMN registration_sent_date DATE;          -- fecha del fichero en el que se envió el alta
ALTER TABLE person ADD COLUMN registration_reason    VARCHAR(32);   -- APV_UR_xx del último rechazo

CREATE TABLE user_registration_feedback (
    id               BIGSERIAL PRIMARY KEY,
    submission_date  DATE      NOT NULL UNIQUE,
    downloaded_at    TIMESTAMP NOT NULL,
    row_count        INTEGER   NOT NULL,
    rejected_count   INTEGER   NOT NULL,
    matched_count    INTEGER   NOT NULL,
    content          BYTEA
);
