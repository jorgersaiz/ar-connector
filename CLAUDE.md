# ar-connector

Conector de Arktic con Activity Report (BBVA). Antes de nada, lee
`docs/estado-del-proyecto.md` (estado, pendientes y cómo montarlo) y, para las
reglas de la API de BBVA, `docs/respuestas-bbva-2026-09.md` y
`docs/activity-report-integracion-proveedores-v1.16.txt`.

## Forma de trabajar

- El usuario es Jorge (Arktic, proveedor de BBVA). Escribe y responde en español.
  Prefiere una recomendación concreta y que después se ponga en marcha.
- Las reglas del documento de BBVA no siempre coinciden con su comportamiento
  real: ante una duda, manda lo que digan sus ficheros reales y sus respuestas.
- Ningún dato de BBVA ni secreto entra en git: `secrets/`, `.env`,
  `samples/real/`, `samples/real-catalogues/` y `backups/` están ignorados.
  Tampoco identificadores reales en los tests.
- No activar `AR_UPLOADS_ENABLED` mientras la base de datos tenga personas de
  prueba.
- Verificar con `cd backend && ./mvnw test` (Windows: `.\mvnw.cmd test`),
  `cd frontend && npm run build` y `docker compose up --build -d`.
- Comandos largos: con límites de tiempo cortos e informando del avance.
