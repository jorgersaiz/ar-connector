-- El uid se guarda siempre en mayúsculas (es como lo hashea Activity Report).
-- Normaliza los registros anteriores; la unicidad de person.uid ya impide
-- que "xe123" y "XE123" convivan a partir de ahora.
UPDATE person SET uid = upper(trim(uid)) WHERE uid <> upper(trim(uid));
