-- =============================================================================
-- Feedback de imputaciones (origen Activity Report). Mismo formato que el
-- fichero enviado más la columna "reason": las filas con reason informada son
-- rechazos (códigos APV_xx). Se descarga al día siguiente del envío.
-- =============================================================================

CREATE TABLE feedback_file (
    id               BIGSERIAL PRIMARY KEY,
    submission_date  DATE      NOT NULL UNIQUE,     -- fecha del fichero de actividades al que responde
    downloaded_at    TIMESTAMP NOT NULL,
    row_count        INTEGER   NOT NULL,
    rejected_count   INTEGER   NOT NULL,
    matched_count    INTEGER   NOT NULL,
    content          BYTEA
);

CREATE TABLE feedback_row (
    id                 BIGSERIAL PRIMARY KEY,
    feedback_file_id   BIGINT       NOT NULL REFERENCES feedback_file (id) ON DELETE CASCADE,
    line_number        INTEGER      NOT NULL,
    activity_date      DATE         NOT NULL,
    person_id          VARCHAR(128) NOT NULL,
    task_level_1_code  VARCHAR(64),
    task_level_2_code  VARCHAR(64),
    task_level_3_code  VARCHAR(64),
    task_level_4_code  VARCHAR(64),
    task_level_5_code  VARCHAR(64),
    sda_project_id     VARCHAR(64),
    jira_issue_id      VARCHAR(64),
    non_sda_project    VARCHAR(255),
    time_minutes       INTEGER,
    reason             VARCHAR(32),                 -- código APV_xx si fue rechazo
    matched            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_feedback_row_file ON feedback_row (feedback_file_id);
CREATE INDEX idx_feedback_row_reason ON feedback_row (reason) WHERE reason IS NOT NULL;

-- Resultado del último feedback recibido para cada día enviado
ALTER TABLE activity_day ADD COLUMN reject_reason VARCHAR(32);      -- rechazo de un día no imputable (fila sin actividad)
ALTER TABLE activity_day ADD COLUMN last_feedback_date DATE;        -- fecha de envío a la que corresponde el último feedback aplicado
