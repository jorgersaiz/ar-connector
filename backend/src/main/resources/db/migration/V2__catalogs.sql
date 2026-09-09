-- =============================================================================
-- Catálogos que Activity Report cede al proveedor (origen: Activity Report).
-- Columnas exactamente como las describe el documento v1.16. Cada
-- sincronización REEMPLAZA el catálogo completo: el fichero es "el catálogo
-- válido para el día de su generación".
-- =============================================================================

-- PROYECTOS SDA
CREATE TABLE sda_project (
    sda_project_id            VARCHAR(64)  PRIMARY KEY,
    description               VARCHAR(500),
    start_quarter             VARCHAR(16),           -- "start_date" del documento: Q de inicio, ej. "1Q 2022"
    end_quarter               VARCHAR(16),           -- "end_date" del documento: Q de fin
    status                    VARCHAR(64),           -- Stock, Flow, On hold, Ended...
    disabled_date             DATE,                  -- imputable hasta ese día incluido
    country                   VARCHAR(128),          -- nombre del país del SDA (casa con countries.sda_country)
    last_modified_status_date DATE,                  -- para calcular el margen de proyectos On hold
    evolutionary_sda          BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at            TIMESTAMP NOT NULL
);

-- ISSUES DE PROYECTOS SDA (features, tasks y conceptualization time de Jira)
CREATE TABLE jira_issue (
    jira_issue_id   VARCHAR(64)  PRIMARY KEY,
    description     VARCHAR(500),
    sda_project_id  VARCHAR(64),
    status          VARCHAR(64),                     -- New, In progress, Test, Ready to Verify, Deployed, Discarded...
    geography       VARCHAR(16),
    disabled_date   DATE,
    type            VARCHAR(64),                     -- feature | task | conceptualization time
    last_synced_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_jira_issue_sda ON jira_issue (sda_project_id);

-- HISTÓRICO DE ESTADOS DE ISSUES (solo cambios de los últimos 30 días)
CREATE TABLE jira_issue_status_history (
    id                 BIGSERIAL PRIMARY KEY,
    jira_issue_key     VARCHAR(64) NOT NULL,
    status             VARCHAR(64) NOT NULL,
    status_change_date DATE        NOT NULL,
    last_synced_at     TIMESTAMP   NOT NULL
);

CREATE INDEX idx_jira_issue_status_history_key ON jira_issue_status_history (jira_issue_key, status_change_date);

-- HISTÓRICO DE FEATURES Y PROYECTOS SDA (solo cambios de la quincena en curso)
CREATE TABLE jira_issue_sda_project_history (
    id              BIGSERIAL PRIMARY KEY,
    jira_issue_key  VARCHAR(64) NOT NULL,
    sda_project_id  VARCHAR(64) NOT NULL,
    change_date     DATE        NOT NULL,            -- "date" del documento
    last_synced_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_jira_issue_sda_project_history_key ON jira_issue_sda_project_history (jira_issue_key, change_date);

-- PROYECTOS NO SDA: el identificador es el propio texto de la descripción
CREATE TABLE non_sda_project (
    description     VARCHAR(255) PRIMARY KEY,
    disabled_date   DATE,
    last_synced_at  TIMESTAMP NOT NULL
);

-- PAÍSES: márgenes de imputación (en trimestres) para proyectos SDA Ended / On hold
CREATE TABLE country (
    country_code    VARCHAR(8)   PRIMARY KEY,
    ended_margin    INTEGER      NOT NULL,           -- 0..4 Qs
    onhold_margin   INTEGER      NOT NULL,           -- 0..4 Qs
    sda_country     VARCHAR(128),                    -- nombre tal como aparece en sda_project.country
    timezone        VARCHAR(64),
    last_synced_at  TIMESTAMP NOT NULL
);

-- NODOS OCULTOS DEL ÁRBOL: persona/día/nodo en el que esa persona NO puede imputar
CREATE TABLE hidden_task_type (
    id              BIGSERIAL PRIMARY KEY,
    person_id       VARCHAR(128) NOT NULL,           -- hash del uid, tal como lo genera Activity Report
    hidden_date     DATE         NOT NULL,           -- "date" del documento
    geography       VARCHAR(16),
    task_type_code  VARCHAR(64)  NOT NULL,
    last_synced_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_hidden_task_type_person_date ON hidden_task_type (person_id, hidden_date);

-- Estado de la última sincronización de cada catálogo
CREATE TABLE catalog_sync_status (
    catalog_name     VARCHAR(64) PRIMARY KEY,
    last_attempt_at  TIMESTAMP,
    last_success_at  TIMESTAMP,
    row_count        INTEGER,
    last_error       TEXT
);
