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
| `ModifiablePeriodPolicy` | 1ª quincena modificable del 1 al 20, 2ª del 15 al 5 del mes+1, validación sobre el día de procesamiento (N+1), sin futuro |
| `TaskTree` | cinco niveles repitiendo el último, solo nodos `can_be_selected`, `disabled_date` inclusivo, exigencia de SDA/Jira/no SDA heredada del camino |
| `ActivitiesFileBuilder` | cabecera y separador `;`, minutos 1..1440, suma diaria ≤ 1440, día no imputable como fila con solo fecha y `person_id`, `technology_detail` siempre vacío, líneas duplicadas (APV_09), campos con `;` |
| `WorkCalendar` | fines de semana y festivos nacionales no se envían; festivos locales van como día no imputable |
| `PeriodSnapshotBuilder` | reconstruye el periodo abierto completo (BBVA sobreescribe el día entero y solo procesa el último fichero del día) |
| `PeriodSnapshotService` | orquestación con Spring/JPA: carga datos, genera fichero, persiste los días materializados desde plantilla |
| `SemicolonCsv` + `CatalogParsers` | lectura de los 8 catálogos por nombre de columna (no por posición), con las columnas del documento; errores por fila con nº de línea |
| `CatalogSyncService` + `CatalogStore` | reemplazo completo de cada catálogo en una transacción; un catálogo que falla conserva su versión anterior y no bloquea al resto; estado por catálogo en `catalog_sync_status` |
| `CatalogSyncJob` | sincronización diaria a las 4:00 (los ficheros están disponibles desde las 3:00) |
| `GET/POST /api/catalogs` | estado de cada catálogo y sincronización manual (`POST /api/catalogs/sync`, `POST /api/catalogs/{nombre}/sync`) |
| `FileSystemActivityReportClient` | modo `AR_CLIENT=filesystem`: lee `samples/catalogues/<catalogo>.csv` y escribe los ficheros subidos en `samples/catalogues/outbox/`, para probar sin credenciales |
| `CallQuotaService` | cuota diaria por canal: 3 llamadas si la última fue OK, 6 si fue KO; el día es el de `jobs.timezone` |
| `ActivitiesUploadService` | reconstruye el periodo abierto, excluye los días inválidos (no bloquea al equipo), no gasta cuota si el fichero es idéntico al último enviado con éxito hoy, registra cada llamada con el fichero exacto en `api_call` y marca los días enviados |
| `UploadJob` | envío a las 13:00, 18:00 y 21:00 todos los días (los cortes pueden caer en fin de semana) |
| `/api/uploads` | `GET /preview` (fichero sin enviar), `POST /run`, `GET /quota`, `GET` histórico, `GET /{id}/file` |
| `FeedbackService` + `FeedbackMatcher` | descarga el feedback de cada envío OK (a partir de las 3:00 del día siguiente), guarda el fichero y sus filas, y marca el `rejectReason` (APV_xx) en la línea o el día correspondiente; un 404 es "todavía no disponible" y se reintenta |
| `FeedbackSyncJob` | 4:30 y 10:30; `/api/feedback` para listar, forzar y ver rechazos |
| `/api/persons` | alta, baja y edición de personas; plantillas por defecto (`/{id}/templates`); días explícitos (`/{id}/days/{fecha}`), validados contra el árbol al guardarlos |
| `/api/holidays` | festivos por geografía y año |
| `POST /api/imports/days` | CSV `uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note`; todo o nada; cada día sustituye al que hubiera |
| Login provisional (`SecurityConfig`, `/api/auth`) | sesión con cookie, usuarios en `app_user` con BCrypt, roles MANAGER / CONSULTANT; un consultor solo ve y edita su propia persona. Se sustituirá por Entra ID |
| `GET /api/persons/{id}/month` | vista de mes para el panel: qué se enviaría cada día (explícito, plantilla, festivo local, nada), huecos, días bloqueados por corte y resultado del último feedback |
| Panel Angular (`frontend/`) | login, "Mi mes" para el consultor, y para el manager personas, plantillas, envíos, rechazos, importación CSV, catálogos, festivos y usuarios. Contrato en `docs/panel-api.md` |

Pendiente (en este orden):

1. Cliente HTTP firmado con la Service Account, cuando BBVA entregue la
   documentación de consumo de APIs (el Requestor va en cabecera). Hoy
   `PendingRequestAuthenticator` solo pone la cabecera `Requestor`.
2. Sustituir el login provisional por Entra ID (y activar CSRF antes de
   exponer el panel fuera de la red de Arktic).
3. Avisos de huecos antes del corte (correo o Teams).
5. Validaciones que dependen de catálogos: márgenes por estado Ended / On hold
   de los SDA, estados de issues de Jira, evolutivos, nodos ocultos por persona.
6. Registro de usuarios nuevos (`upload_user_registration_file`) con su feedback.
7. Infraestructura Azure (Bicep) y pipeline de despliegue.

## Probar la sincronización sin credenciales

```bash
AR_CLIENT=filesystem docker compose up --build -d
curl -X POST http://localhost:8080/api/catalogs/sync
curl http://localhost:8080/api/catalogs
```

Los ficheros de `samples/catalogues/` siguen las columnas del documento y
sirven de plantilla para sustituirlos por muestras reales de BBVA en cuanto
las faciliten.

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

Las credenciales de Activity Report irán en un fichero `.env` no versionado
cuando exista el cliente firmado.

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
- **`person_id` en hex minúsculas**: suposición a verificar contra el catálogo
  `hidden_task_types`, que trae hashes generados por BBVA.
- **Festivos NATIONAL vs LOCAL**: pendiente confirmar con BBVA qué festivos
  conoce Activity Report por geografía.
