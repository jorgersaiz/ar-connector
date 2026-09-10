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

## Catálogos (selectores: cualquier usuario autenticado)

| Ruta | Respuesta |
|---|---|
| GET `/catalogs/task-types` | `[{code, parentCode, path, descriptionEs, canBeSelected, enabled, requiresSdaProject, requiresJiraIssue, requiresNonSdaProject}]` (los `requires*` ya vienen heredados del camino; para imputar usar solo `canBeSelected && enabled`) |
| GET `/catalogs/sda-projects` | `[{sdaProjectId, description, status, evolutionarySda, disabledDate}]` |
| GET `/catalogs/jira-issues?sdaProjectId=` | `[{jiraIssueId, description, sdaProjectId, status, type}]` |
| GET `/catalogs/non-sda-projects` | `[{description, disabledDate}]` |
| GET `/catalogs` (MANAGER) | `[{catalog, lastAttemptAt, lastSuccessAt, rowCount, lastError}]` |
| POST `/catalogs/sync` (MANAGER) | `[{catalog, success, rowCount, error, millis}]` |

## Envíos (MANAGER)

| Ruta | Respuesta |
|---|---|
| GET `/uploads/preview?date=` | `{submissionDate, dayCount, rowCount, excludedDays, problems, gaps: [{uid, date, lastSubmissionDate}], csv}` |
| POST `/uploads/run` | `{status: 'SENT'|'SKIPPED_QUOTA'|'UNCHANGED'|'NOTHING_TO_SEND'|'FAILED'|'NOT_BUILT', submissionDate, apiCallId, dayCount, rowCount, excludedDays, problems, gaps, error}` |
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
