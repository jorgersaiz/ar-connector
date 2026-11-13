# Contrato de la API del panel

Todas las rutas van bajo `/api`. Autenticación por cookie de sesión (mismo
origen; nginx reenvía `/api` al backend). Los errores 400/409 devuelven
`{message, errors: string[], problems: [{uid, date, code, message}]}`.
Un 401 significa "no autenticado" (el panel redirige al login); un 403,
"sin permiso".

Roles: `MANAGER` (todo) y `CONSULTANT` (solo su propia persona: `personId` de `/api/auth/me`).

## Autenticación

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/auth/login` | `{email, password}` | 200 `Me` / 401 |
| POST | `/auth/logout` | | 204 |
| GET | `/auth/me` | | 200 `Me` / 401 |

`Me = {id, email, displayName, role, personId: number|null, personDisplayName: string|null}`

## Usuarios del panel (MANAGER)

| Método | Ruta | Cuerpo |
|---|---|---|
| GET | `/users` | → `User[]` |
| POST | `/users` | `{email, password, displayName, role, personId?}` → 201 `User` |
| PUT | `/users/{id}` | `{email, displayName, role, personId?, active, password?}` → `User` |

`User = {id, email, displayName, role, personId, active}`

## Personas

| Método | Ruta | Quién | Notas |
|---|---|---|---|
| GET | `/persons?active=` | MANAGER | `Person[]` |
| GET | `/persons/{id}` | MANAGER o la propia persona | |
| POST | `/persons` | MANAGER | `Person` sin id → 201; 409 si el uid existe |
| PUT | `/persons/{id}` | MANAGER | |
| DELETE | `/persons/{id}` | MANAGER | baja lógica; devuelve la `Person` |
| GET | `/persons/{id}/templates` | MANAGER o propia | `Template[]` |
| POST | `/persons/{id}/templates` | MANAGER | `{validFrom, validTo?, lines}` → 201 |
| DELETE | `/persons/{id}/templates/{tid}` | MANAGER | 204 |
| GET | `/persons/{id}/days?from&to` | MANAGER o propia | `Day[]` (solo días explícitos) |
| PUT | `/persons/{id}/days/{date}` | MANAGER o propia | `{kind, note?, lines?}` → `Day`; 400 si el periodo está cerrado |
| DELETE | `/persons/{id}/days/{date}` | MANAGER o propia | 204; vuelve a aplicar la plantilla |
| GET | `/persons/{id}/month?year&month` | MANAGER o propia | `MonthView` |

```
Person   = {id, uid, email, displayName, gpsCode, geography, startDate, endDate, active, personId}
Line     = {taskTypeCode, sdaProjectId?, jiraIssueId?, nonSdaProject?, minutes, rejectReason?}
Template = {id, validFrom, validTo, lines: Line[]}
Day      = {id, date, kind: 'WORKED'|'NON_WORKING', origin: 'TEMPLATE'|'MANUAL'|'CSV_IMPORT', note,
            lastSentDate, lastFeedbackDate, rejectReason, hasRejections, totalMinutes, lines: Line[]}
MonthView = {personId, year, month, days: DayView[]}
DayView  = {date, classification: 'WORKING'|'WEEKEND'|'NATIONAL_HOLIDAY'|'LOCAL_HOLIDAY',
            source: 'EXPLICIT'|'TEMPLATE'|'HOLIDAY'|'NONE',
            kind: 'WORKED'|'NON_WORKING'|null, origin, note, lines: Line[], totalMinutes,
            lastSentDate, lastFeedbackDate, hasRejections, rejectReason,
            gap: boolean,            // laborable pasado sin nada que enviar
            locked: boolean,         // el periodo modificable ya cerró: no se puede editar
            lastSubmissionDate,      // último día en que se puede enviar ese día
            future: boolean}         // posterior a hoy
```

## Configuración (cualquier usuario autenticado)

| Ruta | Respuesta |
|---|---|
| GET `/config` | `{environment: 'DEV'|'AU'|'PRO'|'LOCAL'|'OTRO', baseUrl, requestorEmail, credentialsConfigured, uploadsEnabled, gpsCodes: [], geographies: []}`. Con `uploadsEnabled=false` los envíos devuelven `status: 'DISABLED'`. Nada secreto; el panel muestra el entorno y propone el código GPS al dar de alta personas |
| GET `/jobs` (MANAGER) | `[{name, description, cron, lastRunAt, lastTrigger: 'PROGRAMADO'|'RECUPERADO'|null, lastScheduledFor, nextRunAt, pending}]`: estado de los procesos programados. `pending` = se saltó su última hora y el comprobador lo lanzará en los próximos minutos |

## Catálogos (selectores: cualquier usuario autenticado)

| Ruta | Respuesta |
|---|---|
| GET `/catalogs/task-types` | `[{code, parentCode, path, descriptionEs, canBeSelected, enabled, requiresSdaProject, requiresJiraIssue, requiresNonSdaProject, requiresEvolutionarySda, requiresEvolutionaryJiraIssue, requiresConceptualizationTime, jiraIssueRequired, jiraIssueExpected}]` (los `requires*` ya vienen heredados del camino; para imputar usar solo `canBeSelected && enabled`). `jiraIssueExpected`: Activity Report valida la issue en esa actividad (mostrar el campo); `jiraIssueRequired`: además es obligatoria |
| GET `/catalogs/sda-projects?q=&evolutionary=&limit=50` | Búsqueda en servidor (el catálogo real tiene 11.500 proyectos). `q` busca en código y descripción; `evolutionary` = `true` / `false` / vacío; `limit` máximo 200. `[{sdaProjectId, description, status, evolutionarySda, disabledDate}]` |
| GET `/catalogs/jira-issues?q=&sdaProjectId=&withoutSda=&limit=50` | Búsqueda en servidor (266.000 issues). Con `sdaProjectId`, las de ese proyecto; con `withoutSda=true`, las que no cuelgan de ningún SDA (features de evolutivo). Una issue asociada a varios SDA sale una vez por cada uno. `[{jiraIssueId, description, sdaProjectId, status, type}]` |
| GET `/catalogs/non-sda-projects` | `[{description, disabledDate}]` |
| GET `/catalogs` (MANAGER) | `[{catalog, lastAttemptAt, lastSuccessAt, rowCount, lastError}]` |
| POST `/catalogs/sync` (MANAGER) | `[{catalog, success, rowCount, error, millis}]` |

## Envíos (MANAGER)

| Ruta | Respuesta |
|---|---|
| GET `/uploads/preview?date=` | `{submissionDate, dayCount, rowCount, excludedDays, problems, gaps: [{uid, date, lastSubmissionDate}], csv}` |
| POST `/uploads/run` | `{status: 'SENT'|'SKIPPED_QUOTA'|'UNCHANGED'|'NOTHING_TO_SEND'|'FAILED'|'NOT_BUILT'|'DISABLED', submissionDate, apiCallId, dayCount, rowCount, excludedDays, problems, gaps, error}` |
| GET `/uploads/quota` | `{channel, day, callsToday, limit, lastOk}` |
| GET `/uploads` | `[{id, channel, calledAt, submissionDate, ok, httpStatus, errorMessage, fileName, dayCount, rowCount}]` |
| GET `/uploads/{id}/file` | `text/csv` |

## Feedback (MANAGER)

| Ruta | Respuesta |
|---|---|
| GET `/feedback` | `[{submissionDate, downloadedAt, rowCount, rejectedCount, matchedCount}]` |
| GET `/feedback/pending` | `date[]` |
| POST `/feedback/sync` | `[{submissionDate, status: 'APPLIED'|'NOT_AVAILABLE_YET'|'FAILED', rowCount, rejectedCount, matchedRows, unmatchedRows, error}]` |
| GET `/feedback/{date}/rows?onlyRejected=true` | `[{lineNumber, activityDate, personId, taskTypeCode, sdaProjectId, jiraIssueId, nonSdaProject, timeMinutes, reason, matched}]` |

## Festivos

GET `/holidays?geography&year` (autenticado) → `[{id, geography, date, description, scope: 'NATIONAL'|'LOCAL'}]`;
POST `/holidays` y DELETE `/holidays/{id}` (MANAGER).

## Importación CSV (MANAGER)

POST `/imports/days`, multipart con el campo `file` → `{daysCreated, daysReplaced, lines, persons}`.

POST `/imports/persons?dryRun=`, multipart con el campo `file` →
`{dryRun, created, updated, unchanged, registrationsRequested, rows: [{line, uid, displayName, action: 'CREAR'|'ACTUALIZAR'|'SIN_CAMBIOS', changes: [string]}]}`.
Con `dryRun=true` no guarda nada. Si el fichero tiene errores: 400 con `errors` (todos, con su número de línea).
Formato: `uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note`.

## Situación del periodo (MANAGER)

GET `/dashboard` →

```
{
  today, submissionDate,
  periods: [{label, from, to, lastSubmissionDate, daysLeft}],      // quincenas todavía abiertas, en orden
  quota: {callsToday, limit, lastOk},
  lastUpload: {calledAt, submissionDate, ok, dayCount, rowCount, errorMessage} | null,
  pendingFeedbackDates: [date],
  totals: {persons, personsWithGaps, gaps, personsWithRejections, rejections, excludedDays},
  persons: [{personId, uid, displayName, email, hasTemplate,
             gapDates: [date], nextGapDeadline: date|null,
             rejectedDates: [date], excludedDates: [date], problemCodes: [string]}]
}
```

`persons` solo incluye a quien tiene algo que resolver (huecos, rechazos o días excluidos), ordenado por fecha límite más próxima.

## Avisos (MANAGER)

| Ruta | Respuesta |
|---|---|
| GET `/notifications?limit=100` | `[{id, sentAt, channel: 'LOG'|'EMAIL', kind: 'GAPS_REMINDER'|'REJECTIONS'|'MANAGER_SUMMARY', recipientEmail, personId, personDisplayName, subject, body, deadline}]` |
| POST `/notifications/run` | `{sent, skipped, channel, details: [{kind, recipientEmail, subject}]}` — planifica y envía los avisos de hoy (los ya enviados hoy se saltan) |
| GET `/notifications/config` | `{channel, daysBeforeCutoff, mailEnabled}` |

Con `channel = 'LOG'` los avisos se registran pero no se envía correo (no hay SMTP configurado).

## Registro de usuarios en Activity Report (MANAGER)

`Person` incluye ahora `registrationStatus: 'NOT_REQUIRED'|'PENDING'|'SENT'|'REGISTERED'|'REJECTED'`,
`registrationSentDate` y `registrationReason` (APV_UR_xx). En POST/PUT de persona se puede enviar
`registrationStatus` solo con `PENDING` (pedir alta) o `NOT_REQUIRED` (desistir); el resto los pone el conector.

| Ruta | Respuesta |
|---|---|
| GET `/registrations` | `{persons: Person[] (con estado distinto de NOT_REQUIRED), quota: {callsToday, limit, lastOk}, lastUpload: ApiCall|null, pendingFeedbackDates: [date]}` |
| POST `/registrations/run` | `{status: 'SENT'|'SKIPPED_QUOTA'|'NOTHING_TO_SEND'|'FAILED'|'DISABLED', submissionDate, count, apiCallId, error}` |
| POST `/registrations/feedback/sync` | `[{submissionDate, status: 'APPLIED'|'NOT_AVAILABLE_YET'|'FAILED', rowCount, registered, rejected, unmatched, error}]` |
| POST `/registrations/persons/{id}/request` | `Person` (pasa a PENDING; 400 si está SENT o REGISTERED) |
| POST `/registrations/persons/{id}/cancel` | `Person` (pasa a NOT_REQUIRED; 400 si está SENT) |

## CSRF

El backend emite la cookie `XSRF-TOKEN` y exige la cabecera `X-XSRF-TOKEN` en POST/PUT/DELETE.
Angular la envía automáticamente en peticiones relativas; no hay que hacer nada en el panel.
