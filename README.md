# ar-connector — Conector con Activity Report (BBVA)

Conector entre la herramienta interna de imputación de horas del proveedor y la
API de vendors de **Activity Report** de BBVA, según el documento
*"Activity Report - Integración proveedores externos v1.16"*.

Sustituye al prototipo anterior (`bbva-ar`), cuyo modelo de datos y formato de
fichero no se ajustaban al documento. Se mantiene la misma pila por ser el
estándar de la organización: **Java 21 + Spring Boot 3**, **PostgreSQL + Flyway**,
**Docker**. El panel Angular llegará en una fase posterior.

## Qué resuelve

- Los consultores **no entran en Activity Report** (ni por Zscaler ni por VDI).
  Las horas se envían desde un servidor del proveedor con la Service Account
  que entrega BBVA. La API está diseñada para eso.
- **Una sola credencial para todo el equipo.** El fichero lleva una fila por
  persona y día; quien lo envía no tiene por qué ser el consultor.
- **Plantilla por defecto por persona**: el conector genera solo los días
  laborables con la asignación habitual. La persona solo registra excepciones
  (vacaciones, festivos locales, días repartidos entre proyectos).
- **Huecos detectados antes del corte**: los días laborables sin actividad ni
  plantilla se devuelven como `Gap` con su fecha límite de envío, para avisar.

## Estado

Implementado y cubierto con tests (sin necesidad de BD ni credenciales):

| Pieza | Regla del documento que aplica |
|---|---|
| `PersonIdHasher` | `person_id` = SHA256 del uid en mayúsculas |
| `ModifiablePeriodPolicy` | 1ª quincena modificable del 1 al 20, 2ª del 15 al 5 del mes+1, validación sobre el día de procesamiento (N+1), sin futuro. Los últimos días de envío son el 19 y el 4, caigan en el día que caigan: se puede enviar también en fin de semana |
| `TaskTree` | cinco niveles repitiendo el último, solo nodos `can_be_selected`, `disabled_date` inclusivo, exigencia de SDA/Jira/no SDA heredada del camino |
| `ActivitiesFileBuilder` | cabecera y separador `;`, minutos 1..1440, suma diaria ≤ 1440, día no imputable como fila con solo fecha y `person_id`, `technology_detail` siempre vacío, líneas duplicadas (APV_09), campos con `;` |
| `WorkCalendar` | fines de semana y festivos nacionales no se envían; festivos locales van como día no imputable |
| `PeriodSnapshotBuilder` | reconstruye el periodo abierto completo (BBVA sobreescribe el día entero y solo procesa el último fichero del día) |
| `PeriodSnapshotService` | orquestación con Spring/JPA: carga datos, genera fichero, persiste los días materializados desde plantilla |
| `SemicolonCsv` + `CatalogParsers` | lectura de los 8 catálogos por nombre de columna (no por posición), con las columnas de los ficheros reales; descomprime zip y gzip; errores por fila con nº de línea |
| `CatalogSyncService` + `CatalogStore` | reemplazo completo de cada catálogo en una transacción, con inserciones por lotes (JDBC): los 8 catálogos reales, medio millón de filas, se cargan en unos 8 s; un catálogo que falla conserva su versión anterior y no bloquea al resto; estado por catálogo en `catalog_sync_status` |
| `CatalogSyncJob` | sincronización diaria a las 4:00 (los ficheros están disponibles desde las 3:00) |
| `GET/POST /api/catalogs` | estado de cada catálogo y sincronización manual (`POST /api/catalogs/sync`, `POST /api/catalogs/{nombre}/sync`) |
| `FileSystemActivityReportClient` | modo `AR_CLIENT=filesystem`: lee `samples/catalogues/<catalogo>.csv` y escribe los ficheros subidos en `samples/catalogues/outbox/`, para probar sin credenciales |
| `HttpActivityReportClient` + `ServiceAccountRequestAuthenticator` + `GoogleAccessTokenSource` | modo `AR_CLIENT=http`: llamadas al API Gateway de BBVA como "aplicación externa", según su guía de consumo de APIs. Con el JSON de la cuenta de servicio se pide a Google un token (ámbito `email`, delegación opcional) que se renueva solo, y cada petición lleva `Authorization: Bearer`, `Requestor` y `X-BBVA-AppId`. Un fallo anterior al envío (sin credencial, token denegado) no sale hacia BBVA ni cuenta para la cuota |
| `CallQuotaService` | cuota diaria por canal: 3 llamadas si la última fue OK, 6 si fue KO; el día es el de `jobs.timezone` |
| `ActivitiesUploadService` | reconstruye el periodo abierto, excluye los días inválidos (no bloquea al equipo), no gasta cuota si el fichero es idéntico al último enviado con éxito hoy, registra cada llamada con el fichero exacto en `api_call` y marca los días enviados |
| `UploadJob` | envío a las 13:00, 18:00 y 21:00 todos los días (los cortes pueden caer en fin de semana) |
| `CatchUpJob` + `JobRunner` + `MissedRuns` | recuperación de ejecuciones perdidas: cada proceso programado registra su última ejecución en `job_run`; un minuto después de arrancar y cada 10 minutos se lanza, una sola vez, el que se saltó su última hora (equipo apagado o suspendido). Un proceso nunca se ejecuta dos veces para la misma hora. Los avisos de días anteriores no se recuperan. Estado en `GET /api/jobs` y en la pantalla "Situación" |
| `/api/uploads` | `GET /preview` (fichero sin enviar), `POST /run`, `GET /quota`, `GET` histórico, `GET /{id}/file` |
| `FeedbackService` + `FeedbackLocator` + `FeedbackMatcher` | descarga el feedback de cada envío OK (a partir de las 3:00 del día siguiente), guarda el fichero y sus filas, y marca el `rejectReason` (APV_xx) en la línea o el día correspondiente. Se pide con la fecha de envío; como red de seguridad se prueban también las siguientes, y solo se acepta el fichero que corresponde a lo enviado. Un 404, o el 400 "Feedback file not found" que devuelve BBVA, es "todavía no disponible" y se reintenta |
| `FeedbackSyncJob` | 4:30 y 10:30; `/api/feedback` para listar, forzar y ver rechazos |
| `/api/persons` | alta, baja y edición de personas; plantillas por defecto (`/{id}/templates`); días explícitos (`/{id}/days/{fecha}`), validados contra el árbol al guardarlos |
| `/api/holidays` | festivos por geografía y año |
| `POST /api/imports/days` | CSV `uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note`; todo o nada; cada día sustituye al que hubiera |
| Login provisional (`SecurityConfig`, `/api/auth`) | sesión con cookie, usuarios en `app_user` con BCrypt, roles MANAGER / CONSULTANT; un consultor solo ve y edita su propia persona. Se sustituirá por Entra ID |
| `GET /api/persons/{id}/month` | vista de mes para el panel: qué se enviaría cada día (explícito, plantilla, festivo local, nada), huecos, días bloqueados por corte y resultado del último feedback |
| Panel Angular (`frontend/`) | login, "Mi mes" para el consultor, y para el manager personas, plantillas, envíos, rechazos, importación CSV, catálogos, festivos y usuarios. Contrato en `docs/panel-api.md` |
| `DashboardService` + `GET /api/dashboard` | situación del periodo para el manager: quincenas abiertas y sus cortes, cuota, último envío, feedback pendiente y, por persona, huecos, rechazos y días excluidos. Pantalla "Situación", página de inicio del manager |
| `NotificationPlanner` + `NotificationService` | avisos diarios (9:00): recordatorio de huecos a la persona cuando el corte está a `NOTIFY_DAYS_BEFORE_CUTOFF` días (2 por defecto), rechazos pendientes, y resumen a los managers. Un aviso por destinatario, tipo y día. Sin SMTP (`MAIL_HOST`, `MAIL_ENABLED=true`) solo se registran en `notification` |
| CSRF (`SecurityConfig`) | cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN` (receta de Spring Security para SPA); Angular la envía sola |
| `UserRegistrationService` + `/api/registrations` | alta de personas nuevas en Activity Report: fichero `uids` con las PENDING (cuota propia), feedback `uid;reason` (hash en mayúsculas) al día siguiente que las deja REGISTERED (ajustando la fecha de alta al día siguiente del envío) o REJECTED con su APV_UR_xx. Pantalla "Altas en AR" |
| `CatalogRules` | reglas que dependen de catálogos, aplicadas al guardar y al generar el fichero: SDA existente y vigente, márgenes en trimestres para Ended (desde `end_date`) y On hold (desde `last_modified_status_date`) según `countries`, SDA e issues de evolutivo solo en actividades de evolutivo, issues existentes y del SDA indicado en la fecha de actividad (catálogo o histórico), Discarded solo hasta el día del cambio, Deployed hasta 30 días después, conceptualization time solo en su actividad, proyectos no SDA del catálogo, nodos ocultos por persona y día. Las issues solo se validan en las actividades que las esperan, como hace Activity Report. Contrastadas con un envío real de 9.500 líneas y su feedback (`RealSamplesTest`): ninguna discrepancia |
| `GET /api/catalogs/sda-projects`, `/jira-issues` | búsqueda en servidor para los selectores del panel (11.500 proyectos y 266.000 issues en los catálogos reales) |

Las respuestas de BBVA de septiembre de 2026, lo que enseñan sus ficheros de
ejemplo y lo que sigue pendiente están en `docs/respuestas-bbva-2026-09.md`.

Pendiente (en este orden):

1. Primera conexión real, cuando BBVA entregue la credencial y confirme que
   la cuenta de servicio está autorizada en el API Gateway. Pasos en
   "Conectar con Activity Report".
2. Sustituir el login provisional por Entra ID.
3. Tests de integración contra PostgreSQL (Testcontainers).
4. Infraestructura Azure (Bicep) y pipeline de despliegue.

Datos de infraestructura de Arktic que harán falta: SMTP para los avisos
(`MAIL_HOST`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`), URL pública del
panel (`PANEL_URL`) y el registro de la aplicación en Entra ID.

## Conectar con Activity Report

1. Guardar el JSON de la cuenta de servicio del entorno en `secrets/` (git
   ignora la carpeta; ver `secrets/LEEME.md`). Hay una credencial por entorno.
2. Copiar `.env.example` como `.env` y rellenar `AR_CLIENT=http`,
   `AR_BASE_URL` (entorno), `AR_REQUESTOR_EMAIL` y `AR_CREDENTIALS_FILE`.
   El resto de valores de autenticación ya coinciden con lo que confirmó BBVA.
3. `docker compose up --build -d`. El log del backend dice con qué credencial,
   ámbito y Requestor arranca, y la pantalla "Situación" muestra el entorno.
4. Primera prueba, de solo lectura: en el panel, Catálogos → sincronizar
   (o `POST /api/catalogs/countries/sync`). El error, si lo hay, queda en la
   fila del catálogo.

| Si falla con | Causa probable | Qué cambiar |
|---|---|---|
| "No existe el fichero de la credencial" | ruta mal puesta: tiene que ser la de dentro del contenedor | `AR_CREDENTIALS_FILE=/app/secrets/<fichero>.json` |
| "Google no ha concedido el token ... unauthorized_client" | se pide delegación y la cuenta de servicio no la tiene | `AR_DELEGATE_TO_REQUESTOR=false` |
| "Google no ha concedido el token ... invalid_grant" | credencial retirada o reloj del equipo desajustado | pedir la credencial vigente; sincronizar la hora |
| Activity Report respondió 401 | el gateway no acepta el token: credencial de otro entorno, o retirada | comprobar que `AR_CREDENTIALS_FILE` es la del entorno de `AR_BASE_URL` |
| Activity Report respondió 403 | el Requestor no está registrado para Arktic, o la autorización en el gateway no está hecha | pedir a BBVA que lo revise |
| Activity Report respondió 400 al subir | el cuerpo del 400 dice qué falla (cabecera, columnas...) | ver el mensaje en Envíos |

## Probar la sincronización sin credenciales

```bash
AR_CLIENT=filesystem docker compose up --build -d
curl -X POST http://localhost:8080/api/catalogs/sync
curl http://localhost:8080/api/catalogs
```

Los ficheros de `samples/catalogues/` son inventados y siguen el formato de
los reales. Los de ejemplo que ha facilitado BBVA son datos suyos y no se
versionan (`samples/real/` y `samples/real-catalogues/` están en `.gitignore`):

- `samples/real/`: los CSV tal como los envió BBVA. Con ellos presentes,
  `./mvnw test` ejecuta además `RealSamplesTest`, que compara las reglas del
  conector con el veredicto de Activity Report línea a línea.
- `samples/real-catalogues/`: los 8 catálogos renombrados como
  `<catalogo>.csv`. Para arrancar con ellos:
  `AR_CLIENT=filesystem AR_LOCAL_DIR=/app/samples/real-catalogues docker compose up --build -d`.

Las variables de entorno están descritas en `.env.example`.

Para probar el envío hace falta al menos una persona con plantilla (todavía
no hay API de alta; se insertan en SQL):

```sql
insert into person (uid,email,display_name,gps_code,geography,start_date)
  values ('XE12345','ana@vendor.com','Ana','GPS001','ES','2026-01-01');
insert into default_template (person_id, valid_from) select id, '2026-01-01' from person where uid='XE12345';
insert into default_template_line (default_template_id, task_type_code, sda_project_id, minutes)
  select id, '01.01.01', 'SDATOOL-37040', 480 from default_template;
```

```bash
curl http://localhost:8080/api/uploads/preview      # fichero, huecos y días excluidos, sin enviar
curl -X POST http://localhost:8080/api/uploads/run  # envía (en modo filesystem, a samples/catalogues/outbox/)
curl http://localhost:8080/api/uploads/quota
```

## Ejecutar los tests

Solo necesita Java 21; el wrapper descarga Maven la primera vez.

```bash
cd backend && ./mvnw test
```

En Windows con PowerShell o cmd: `.\mvnw.cmd test`.

## Arrancar en local

```bash
docker compose up --build
```

- Panel: http://localhost:4200 (usuario inicial `admin@arktic.local` / `admin1234`,
  configurable con `ADMIN_EMAIL` / `ADMIN_PASSWORD`; solo se crea si no hay usuarios)
- API backend: http://localhost:8080
- PostgreSQL: localhost:5432 (`ar_connector` / `changeme`)

Sin `.env`, el backend arranca en modo `http` sin credencial: el panel
funciona, pero para ver catálogos y probar envíos hay que arrancar con
`AR_CLIENT=filesystem` o configurar la credencial.

## Copias de seguridad

Al arrancar con `docker compose up`, el servicio `backup` hace una copia
completa de la base de datos cada 24 horas y conserva las de los últimos 30
días. No usa una hora fija: la hace en cuanto la última tiene más de 24 horas,
de modo que un PC que se apaga por la noche la hace al arrancar por la mañana.

Las copias quedan en la carpeta `backups/` (git la ignora), una por fichero:
`ar_connector_<fecha>_<hora>_<tipo>.dump`. Con los catálogos reales cargados
cada una ocupa unos 8 MB y tarda 4 segundos.

| Para | Comando |
|---|---|
| Ver que las copias se están haciendo | `docker compose logs backup` |
| Hacer una copia ahora (antes de un cambio delicado) | `docker compose run --rm backup backup.sh manual` |
| Ver las copias disponibles | `docker compose run --rm backup restore.sh` |
| Restaurar una copia | `docker compose run --rm backup restore.sh <fichero>` y después `docker compose restart backend` |

La restauración sustituye la base de datos entera, pide confirmación y es
segura ante fallos: restaura en una base de datos aparte y solo si termina
bien sustituye a la actual; antes guarda una copia del estado que había
(`..._antes-de-restaurar.dump`). Las copias manuales y las de antes de
restaurar no se borran solas.

Dos cosas a tener en cuenta:

- **Las copias están en el mismo disco que la base de datos.** Protegen de un
  borrado o de un volumen de Docker perdido, no de la avería o pérdida del PC.
  Para eso, `BACKUP_DIR` en el `.env` tiene que apuntar a una carpeta
  sincronizada o de red de Arktic.
- **Contienen datos personales** (nombres, correos, imputaciones): la carpeta
  de destino tiene que ser una a la que solo acceda quien deba.

Restaurar no deshace lo que ya se haya enviado a Activity Report. Tras
restaurar una copia antigua, el siguiente envío vuelve a mandar el periodo
abierto tal como esté en la copia.

## Estructura

```
backend/src/main/java/com/vendor/arconnector/
├── domain/        Entidades JPA: Person, Holiday, TaskType, ActivityDay, ActivityLine,
│                  DefaultTemplate, DefaultTemplateLine
├── activities/    Lógica pura (sin Spring) del fichero de imputaciones y del periodo abierto
├── repository/    Spring Data JPA
└── service/       Orquestación (PeriodSnapshotService)
backend/src/main/resources/db/migration/   Migraciones Flyway (V1 = esquema inicial)
backend/src/test/java/.../activities/      Tests contra las reglas del documento
docs/                                      Texto extraído del documento de BBVA, como referencia
```

## Decisiones de diseño

- **El día completo es la unidad.** `ActivityDay` con sus `ActivityLine` en
  minutos. Enviar líneas sueltas borraría en BBVA el resto del día.
- **Cada envío lleva todo el periodo abierto**, no solo cambios.
- **La exigencia de proyecto SDA se hereda del antecesor**: el documento dice
  que "01" es la única actividad con `requires_sda_project` a true y que sus
  descendientes son las actividades de proyectos SDA.
- **Validar antes de enviar, no después.** El builder rechaza el fichero entero
  con la lista completa de problemas; BBVA ignora filas inválidas en silencio
  y solo lo cuenta en el feedback del día siguiente.
- **`person_id` en hex minúsculas**: Activity Report lo acepta así y lo
  devuelve en mayúsculas (feedback, `hidden_task_types`), por lo que todas
  las comparaciones de hash ignoran mayúsculas.
- **Festivos NATIONAL vs LOCAL**: confirmado por BBVA que Activity Report
  solo conoce los nacionales; los locales se envían como día no imputable.
