-- =============================================================================
-- Esquema inicial del conector con Activity Report (BBVA).
-- Modelo alineado con el documento "Integración proveedores externos v1.16":
--  * la unidad de envío es el DÍA COMPLETO de una persona (BBVA sobreescribe
--    el día entero en cada carga),
--  * el tiempo se expresa en MINUTOS enteros (1..1440),
--  * el uid se envía anonimizado (SHA256 del uid en mayúsculas), pero aquí
--    se guarda en claro porque hace falta para el registro de usuarios.
-- =============================================================================

-- Personas del proveedor en perímetro de Activity Report
CREATE TABLE person (
    id            BIGSERIAL PRIMARY KEY,
    uid           VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    gps_code      VARCHAR(32)  NOT NULL,
    geography     VARCHAR(8)   NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Calendario laboral: festivos por geografía.
--   NATIONAL: festivo que Activity Report ya conoce -> no se envía (lo rechazaría con APV_01).
--   LOCAL:    festivo local -> se envía como día no imputable (fecha + person_id, resto vacío).
CREATE TABLE holiday (
    id           BIGSERIAL PRIMARY KEY,
    geography    VARCHAR(8)   NOT NULL,
    holiday_date DATE         NOT NULL,
    description  VARCHAR(255) NOT NULL,
    scope        VARCHAR(16)  NOT NULL,
    CONSTRAINT uq_holiday UNIQUE (geography, holiday_date)
);

-- Catálogo "ÁRBOL DE ACTIVIDADES" (columnas reales del documento, origen Activity Report)
CREATE TABLE task_type (
    task_type_id                     VARCHAR(64) PRIMARY KEY,
    task_type_parent_id              VARCHAR(64),
    description_es                   VARCHAR(500),
    description_en                   VARCHAR(500),
    help_es                          TEXT,
    help_en                          TEXT,
    requires_sda_project             BOOLEAN NOT NULL DEFAULT FALSE,
    requires_jira_issue              BOOLEAN NOT NULL DEFAULT FALSE,
    requires_non_sda_project         BOOLEAN NOT NULL DEFAULT FALSE,
    requires_technology_detail       BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order                       INTEGER,
    disabled_date                    DATE,
    can_be_selected                  BOOLEAN NOT NULL DEFAULT FALSE,
    requires_evolutionary_sda        BOOLEAN NOT NULL DEFAULT FALSE,
    requires_evolutionary_jira_issue BOOLEAN NOT NULL DEFAULT FALSE,
    requires_conceptualization_time  BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at                   TIMESTAMP NOT NULL
);

-- Día de actividad de una persona (unidad de envío a Activity Report)
CREATE TABLE activity_day (
    id             BIGSERIAL PRIMARY KEY,
    person_id      BIGINT      NOT NULL REFERENCES person (id),
    activity_date  DATE        NOT NULL,
    kind           VARCHAR(16) NOT NULL,   -- WORKED | NON_WORKING
    origin         VARCHAR(16) NOT NULL,   -- TEMPLATE | MANUAL | CSV_IMPORT
    note           VARCHAR(255),
    last_sent_date DATE,                   -- fecha del último fichero en el que fue este día
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_day UNIQUE (person_id, activity_date)
);

CREATE INDEX idx_activity_day_date ON activity_day (activity_date);

-- Líneas de un día trabajado: actividad (nodo hoja del árbol) + proyecto/issue + minutos
CREATE TABLE activity_line (
    id               BIGSERIAL PRIMARY KEY,
    activity_day_id  BIGINT      NOT NULL REFERENCES activity_day (id) ON DELETE CASCADE,
    task_type_code   VARCHAR(64) NOT NULL,
    sda_project_id   VARCHAR(64),
    jira_issue_id    VARCHAR(64),
    non_sda_project  VARCHAR(255),
    minutes          INTEGER     NOT NULL,
    sort_order       INTEGER     NOT NULL DEFAULT 0,
    reject_reason    VARCHAR(32),           -- código APV_xx del último feedback, si fue rechazada
    CONSTRAINT ck_activity_line_minutes CHECK (minutes BETWEEN 1 AND 1440)
);

-- Plantilla por defecto de una persona: lo que se imputa cada día laborable
-- que no tenga un activity_day explícito.
CREATE TABLE default_template (
    id          BIGSERIAL PRIMARY KEY,
    person_id   BIGINT    NOT NULL REFERENCES person (id),
    valid_from  DATE      NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_default_template_person ON default_template (person_id, valid_from);

CREATE TABLE default_template_line (
    id                   BIGSERIAL PRIMARY KEY,
    default_template_id  BIGINT      NOT NULL REFERENCES default_template (id) ON DELETE CASCADE,
    task_type_code       VARCHAR(64) NOT NULL,
    sda_project_id       VARCHAR(64),
    jira_issue_id        VARCHAR(64),
    non_sda_project      VARCHAR(255),
    minutes              INTEGER     NOT NULL,
    sort_order           INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT ck_default_template_line_minutes CHECK (minutes BETWEEN 1 AND 1440)
);
