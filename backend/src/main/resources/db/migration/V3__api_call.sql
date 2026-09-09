-- =============================================================================
-- Registro de cada llamada de subida a Activity Report. Sirve para:
--  * aplicar la cuota diaria del documento (máx. 3 llamadas/día si la última
--    fue OK, máx. 6 si fue KO), por canal,
--  * conservar el fichero exacto enviado (BBVA solo procesa el último del día
--    y el feedback llega al día siguiente),
--  * no volver a gastar cuota si el contenido no ha cambiado.
-- =============================================================================
CREATE TABLE api_call (
    id               BIGSERIAL PRIMARY KEY,
    channel          VARCHAR(32)  NOT NULL,        -- ACTIVITIES_UPLOAD | USER_REGISTRATION_UPLOAD
    called_at        TIMESTAMP    NOT NULL,
    submission_date  DATE         NOT NULL,        -- día N de envío (se procesa el N+1)
    ok               BOOLEAN      NOT NULL,
    http_status      INTEGER,
    error_message    TEXT,
    file_name        VARCHAR(128),
    day_count        INTEGER,
    row_count        INTEGER,
    content_sha256   VARCHAR(64),
    content          BYTEA
);

CREATE INDEX idx_api_call_channel_called_at ON api_call (channel, called_at);
