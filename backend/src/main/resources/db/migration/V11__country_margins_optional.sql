-- En el catálogo countries de DEV (noviembre 2026) hay países con onhold_margin vacío.
-- Sin margen no se puede comprobar la regla y el conector no bloquea la imputación.
ALTER TABLE country ALTER COLUMN ended_margin DROP NOT NULL;
ALTER TABLE country ALTER COLUMN onhold_margin DROP NOT NULL;
