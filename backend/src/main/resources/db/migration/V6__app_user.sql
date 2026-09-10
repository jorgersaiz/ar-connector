-- =============================================================================
-- Usuarios del panel con login provisional (email + contraseña BCrypt).
-- Se sustituirá por Entra ID cuando Arktic registre la aplicación en su
-- tenant; la tabla seguirá sirviendo para el rol y el enlace con la persona.
-- =============================================================================
CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    role           VARCHAR(16)  NOT NULL,          -- MANAGER | CONSULTANT
    person_id      BIGINT       REFERENCES person (id),   -- persona cuyas horas puede ver/editar (opcional para managers)
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_user_person ON app_user (person_id);
