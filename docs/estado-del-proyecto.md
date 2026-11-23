# Estado del proyecto (13/11/2026)

Documento de traspaso para continuar el trabajo desde otro equipo. El detalle
de las respuestas de BBVA está en `respuestas-bbva-2026-09.md`; el
funcionamiento, en el `README.md` de la raíz.

## Qué es

Conector entre Arktic y Activity Report (BBVA). Un gestor imputa por todo el
equipo (a mano, con plantillas o importando CSV) y el conector envía cada día
el fichero de imputaciones a la API de proveedores de BBVA, descarga sus
catálogos y el feedback de rechazos, y avisa de huecos.

Stack: Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway, Angular 18, Docker
Compose. 196 tests de backend (`cd backend && ./mvnw test`).

## Dónde estamos

- Todo lo que no depende de BBVA está hecho y probado: panel, plantillas,
  importación CSV, envío con cuota, feedback, altas de usuarios, avisos,
  copias de seguridad automáticas.
- Las reglas de validación se contrastaron con un envío real de 9.500 líneas
  y su feedback: ninguna discrepancia (`RealSamplesTest`, solo con los
  ficheros reales presentes).
- Autenticación con BBVA implementada y funcionando: cuenta de servicio de
  Google, token sin delegar, cabeceras `Authorization`, `Requestor` y
  `X-BBVA-AppId` (= `client_id` del JSON).
- **Conexión con DEV:** desde el 23/11/2026 se descargan los 8 catálogos
  (cuatro llegan vacíos en DEV) y los dos feedback pasan la autorización. Las
  subidas están sin probar.
- El conector corre en **solo lectura** (`AR_UPLOADS_ENABLED=false`): descarga
  pero no sube nada. La base de datos local solo tiene personas de prueba
  (Ana, Bruno, Carla); no activar los envíos hasta sustituirlas por gente real.

## Qué falta

De BBVA (ver la lista completa en `respuestas-bbva-2026-09.md`):

1. Confirmar el permiso de subida en DEV y si es normal que cuatro catálogos
   lleguen vacíos.
2. Saber si AU y PRO cambian de dominio como DEV, y sus credenciales.
3. Fecha a partir de la cual se deja de imputar a mano (cada envío
   sobrescribe el día completo de la persona).

Del lado de Arktic:

4. Lista de personas del piloto (uid XE..., correo, geografía, GPS) y sus
   plantillas con códigos de actividad reales.
5. Dónde se guardan las copias de seguridad (`BACKUP_DIR`): hoy en el mismo
   disco que la base de datos.

Resuelto el 13/11/2026: si el PC está apagado a la hora de un proceso
(catálogos 4:00, feedback 4:30/10:30, avisos 9:00, envíos 13:00/18:00/21:00),
el conector lo ejecuta al encenderse (`CatchUpJob`). Aun así, el día de corte
(19 y 4) conviene que el PC esté encendido en algún momento antes de las 24:00.

Resuelto el 13/11/2026: alta masiva de personas desde CSV (Importar CSV →
pestaña Personas, o el botón "Importar CSV" de Personas), con un paso de
"Comprobar" que enseña qué se va a crear o cambiar antes de guardar.

Para el piloto: con la lista de personas de Arktic, importarlas y crear a
cada una su plantilla por defecto (hoy, una a una en su ficha).

Pospuesto: login con Entra ID, SMTP para los avisos e infraestructura en
Azure (dependen de decisiones de infraestructura de Arktic).

## Montarlo en otro equipo

Necesita Docker Desktop y Git. Para tocar código, además Java 21 (el wrapper
`mvnw` descarga Maven) y Node 20+.

1. Clonar el repositorio.
2. Copiar **a mano, por un canal interno de Arktic** (nunca por correo
   externo, chat ni git) lo que no está en el repositorio:
   - `secrets/dev-bbva-timereport-sp-prv-arktic.json`: la clave de DEV.
   - `.env`: la configuración local. Si no se copia, crearlo desde
     `.env.example` con estos valores para DEV:

     ```
     AR_CLIENT=http
     AR_BASE_URL=https://dev-gag.bbva.com/timereport/api/vendors
     AR_REQUESTOR_EMAIL=<cuenta @dev.bbva.com del Requestor>
     AR_CREDENTIALS_FILE=/app/secrets/dev-bbva-timereport-sp-prv-arktic.json
     AR_UPLOADS_ENABLED=false
     ```
   - Opcional: `samples/real/` y `samples/real-catalogues/` (ficheros de
     ejemplo de BBVA, activan `RealSamplesTest`) y la carpeta `backups/`.
3. `docker compose up --build -d` y abrir http://localhost:4200
   (`admin@arktic.local` / `admin1234`). La pantalla "Situación" muestra el
   entorno al que está conectado.

## Cosas que conviene saber

- Si otro proyecto del equipo usa el puerto 5432, la base de datos no
  arranca y el backend falla con `UnknownHostException: db`: poner
  `DB_HOST_PORT=5433` en el `.env`.
- Ejecutar el jar fuera de Docker puede fallar por políticas del equipo:
  verificar siempre con `docker compose up --build`.
- Los ficheros reales de BBVA, la clave, el `.env` y las copias de seguridad
  están en `.gitignore`. Antes de cualquier commit, comprobar que no entran.
