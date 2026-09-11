-- =============================================================================
-- Festivos nacionales de España 2026 (calendario laboral publicado en el BOE).
-- Son los de ámbito nacional no sustituibles más el Jueves Santo, que es
-- nacional pero algunas comunidades (Cataluña, Comunidad Valenciana) lo
-- sustituyen. Todos los Santos (1 nov) y la Constitución (6 dic) caen en
-- domingo en 2026: las comunidades que los trasladan al lunes deben añadirlos.
-- Se cargan como NATIONAL (BBVA los conoce: no se envía nada ese día).
-- Pendiente confirmar con Activity Report qué festivos considera por geografía.
-- =============================================================================
INSERT INTO holiday (geography, holiday_date, description, scope) VALUES
    ('ES', '2026-01-01', 'Año Nuevo', 'NATIONAL'),
    ('ES', '2026-01-06', 'Epifanía del Señor', 'NATIONAL'),
    ('ES', '2026-04-02', 'Jueves Santo (sustituible en algunas comunidades)', 'NATIONAL'),
    ('ES', '2026-04-03', 'Viernes Santo', 'NATIONAL'),
    ('ES', '2026-05-01', 'Fiesta del Trabajo', 'NATIONAL'),
    ('ES', '2026-08-15', 'Asunción de la Virgen', 'NATIONAL'),
    ('ES', '2026-10-12', 'Fiesta Nacional de España', 'NATIONAL'),
    ('ES', '2026-12-08', 'Inmaculada Concepción', 'NATIONAL'),
    ('ES', '2026-12-25', 'Natividad del Señor', 'NATIONAL')
ON CONFLICT (geography, holiday_date) DO NOTHING;
