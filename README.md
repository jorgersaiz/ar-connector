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

Pendiente (en este orden):

1. Sincronización de catálogos con las columnas reales del documento
   (`task_types`, `sda_projects`, `jira_issues`, `jira_issues_historical`,
   `jira_issue_sda_projects_historical`, `non_sda_projects`, `countries`,
   `hidden_task_types`). `other_technologies` se eliminó en la v1.16.
2. Cliente HTTP firmado con la Service Account, cuando BBVA entregue la
   documentación de consumo de APIs (el Requestor va en cabecera).
3. Job diario de envío + control de cuota (3 llamadas/día si la última fue OK, 6 si KO).
4. Descarga y procesado del feedback (`APV_xx`) a la mañana siguiente.
5. Importación CSV para el manager, avisos de huecos antes del corte, panel web con roles.
6. Validaciones que dependen de catálogos: márgenes por estado Ended / On hold
   de los SDA, estados de issues de Jira, evolutivos, nodos ocultos por persona.

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
