-- =============================================================================
-- Ajustes tras contrastar con los ficheros reales de la API (septiembre 2026).
-- =============================================================================

-- ISSUES DE JIRA: una misma issue puede venir en varias filas, una por cada
-- proyecto SDA asociado (3.188 casos en la muestra real), y más de la mitad no
-- tienen SDA. La clave deja de ser el id de la issue.
ALTER TABLE jira_issue DROP CONSTRAINT jira_issue_pkey;
ALTER TABLE jira_issue ADD COLUMN id BIGSERIAL PRIMARY KEY;
CREATE INDEX idx_jira_issue_key ON jira_issue (jira_issue_id);
-- La geografía no es un código de país: "Global - Retail Client Solutions", "HOLDING ARQ"...
ALTER TABLE jira_issue ALTER COLUMN geography TYPE VARCHAR(64);
ALTER TABLE hidden_task_type ALTER COLUMN geography TYPE VARCHAR(64);

-- HISTÓRICO DE FEATURES Y SDA: el SDA viene vacío cuando la feature se queda sin proyecto.
ALTER TABLE jira_issue_sda_project_history ALTER COLUMN sda_project_id DROP NOT NULL;

-- FEEDBACK: fecha con la que Activity Report devolvió el fichero (la de envío o
-- la de procesamiento, que es la del día siguiente).
ALTER TABLE feedback_file ADD COLUMN feedback_date DATE;
ALTER TABLE user_registration_feedback ADD COLUMN feedback_date DATE;
