-- Última ejecución de cada proceso programado, para recuperar las que no se
-- hicieron porque el equipo estaba apagado o la aplicación parada.
CREATE TABLE job_run (
    job_name          VARCHAR(64)  PRIMARY KEY,
    last_run_at       TIMESTAMPTZ  NOT NULL,
    -- PROGRAMADO (a su hora) o RECUPERADO (ejecutado más tarde por el comprobador)
    last_trigger      VARCHAR(16)  NOT NULL,
    -- hora programada a la que correspondía esa ejecución
    last_scheduled_for TIMESTAMPTZ NOT NULL
);
