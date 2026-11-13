# Respuestas de BBVA y contraste con sus ficheros de ejemplo (septiembre 2026)

Reunión del 22/09/2026 y documentación recibida después: la carpeta
"API VENDORS - Ejemplo de ficheros devueltos y recibidos" (catálogos del
25/09/2026, un envío de imputaciones del 24/09 con su feedback, un registro de
usuarios con su feedback) y el Excel "[API Vendors] Códigos de error / rechazos".

Los ficheros de ejemplo son datos de BBVA y **no se versionan**. Copiados en
`samples/real/` (ignorado por git) activan `RealSamplesTest`.

## Respuestas

| Tema | Respuesta de BBVA | Efecto en el conector |
|---|---|---|
| Credencial | Una cuenta de servicio para Arktic, JSON con clave privada. La envían por Google Drive a los emails de contratista que se usen como Requestor. Rota cada enero; avisan ellos. La clave antigua no se borra hasta que Arktic confirme que la nueva funciona | Sin cambios. Conviene recordárselo por correo en enero |
| Documentación de firma | Es la guía de consumo de APIs de platform.bbva.com (recibida el 29/09). Arktic es una "aplicación externa": token OAuth de la cuenta de servicio con ámbito `email` y cabeceras `Authorization: Bearer`, `Requestor` y `X-BBVA-AppId` | Implementado con la librería oficial de Google; ver "Autenticación" |
| Entornos | DEV `dev-bbva-gateway.appspot.com` (cuentas @dev.bbva.com), AU `au-bbva-gateway.appspot.com` y PRO `bbva-gateway.appspot.com` (cuentas @bbva.com) | En `.env.example` |
| Conectividad | Accesible desde Internet | El piloto se puede hacer desde un portátil |
| Requestor | Una o varias cuentas `xxxxx.yyyyy.contractor@bbva.com` | Sin cambios |
| Error 400 | `{"code":400,"error_code":"INVALID_CONFIGURATION","message":"[API VENDORS] Feedback file not found."}` | Un feedback que aún no existe devuelve 400, no 404: se trata como "todavía no disponible" |
| Procesamiento N+1 | Se puede enviar cualquier día, también sábados y domingos, y el fichero se procesa al día siguiente (aclarado el 29/09; la primera lectura de la respuesta, "no corre en fin de semana", era errónea) | Los últimos días de envío son el 19 y el 4, sin ajustes |
| Fecha del feedback | Se pide con la fecha del envío | Es la primera que prueba `FeedbackLocator`; conserva las siguientes como red de seguridad |
| Festivos | Activity Report solo conoce los nacionales. Los locales se envían como día no imputable | Era lo implementado |
| Herencia en el árbol | Las exigencias del nivel superior se aplican a las hojas | Era lo implementado |
| Identificador de persona | SHA256 del código tipo XE123456 en mayúsculas | Era lo implementado |
| Nombre del campo multipart | `file`, en `multipart/form-data` (ejemplo en Python con `requests`, 06/10) | Es lo implementado |
| Códigos GPS | "ARKTIC BUSINESS CONSULTING SL [ESP] [B56152515]": `0000417143` (06/10) | `AR_GPS_CODES`; el panel lo propone al dar de alta personas |
| Convivencia con la imputación manual | No contestado | Pendiente: acordar fecha de corte |

## Lo que enseñan los ficheros reales

### Catálogos

| Catálogo | Filas | Hallazgo | Cambio |
|---|---|---|---|
| `task_types` | 630 | Tres raíces (01 con SDA, 02 sin SDA, 03 otros), profundidad máxima 3. Las exigencias están también en nodos intermedios | Ninguno |
| `sda_projects` | 11.534 | Trimestres como `4Q2026` y `4Q 2026`; países en mayúsculas | Selector con búsqueda en servidor |
| `jira_issues` | 266.443 | **La misma issue se repite, una fila por SDA** (3.188 casos). Más de la mitad no tienen SDA. La geografía es un área funcional de hasta 36 caracteres. Tipos `Feature`, `Task`, `Conceptualization Time` | Clave propia, carga por lotes con JDBC, selector con búsqueda en servidor |
| `jira_issues_historical` | 185.508 | Cubre unos 40 días | Carga por lotes |
| `jira_issue_sda_projects_historical` | 41.966 | **El SDA viene vacío** en tres de cada cuatro filas: la feature se quedó sin proyecto | Columna opcional |
| `non_sda_projects` | 1.421 | Se identifican por su descripción | Selector con filtro |
| `countries` | 10 | Como en el documento | Ninguno |
| `hidden_task_types` | 108 | **Hash en mayúsculas**. Oculta también nodos intermedios (03.04), luego afecta a sus descendientes | Comparación sin distinguir mayúsculas |

Ningún fichero trae comillas ni `;` dentro de los campos. Los catálogos usan
saltos de línea LF; los ficheros de envío y feedback, CRLF.

### Envío y feedback de imputaciones

- El proveedor del ejemplo envía el hash en **minúsculas** y Activity Report lo
  acepta; el feedback lo devuelve en **mayúsculas**.
- El feedback contiene exactamente las mismas filas que el envío, en otro orden.
- El envío del 24/09 tiene un feedback llamado `..._20260925_feedback.csv`: el
  nombre del fichero lleva la fecha de procesamiento. Según BBVA se pide con la
  fecha de envío; `FeedbackLocator` prueba esa primero y después las
  siguientes, y solo acepta el fichero cuyo contenido corresponde a lo enviado.
- El código de conceptualization time es `APV_05_CT` (el documento decía
  `APV_05_EV_CT`).

### Reglas contrastadas con 9.500 líneas reales

Aplicando las reglas del conector a cada línea del envío y comparando con el
veredicto de Activity Report, había 321 líneas que el conector habría bloqueado
y BBVA aceptó. Corregido:

1. **Donde la actividad no exige issue de Jira, Activity Report no la valida.**
   Acepta issues desconocidas, de otro SDA o desplegadas hace meses. El conector
   ya no las valida ahí (antes las rechazaba).
2. **Una issue puede ir sin SDA.** "Features de evolutivo" (03.04.02) exige
   issue y no cuelga de proyectos SDA. Se elimina la regla "issue sin SDA".
3. **La asociación issue-SDA se evalúa en la fecha de actividad.** Una feature
   que cambió de SDA el día 22: los días anteriores con el SDA antiguo se
   aceptan, del 22 en adelante se rechazan. Como el catálogo no informa el SDA
   anterior, los días previos a un cambio no se pueden comprobar y se dejan pasar.
4. La actividad de conceptualization time (01.01) exige issue aunque no tenga
   marcado `requires_jira_issue`.

Tras los cambios: **0 líneas bloqueadas de más y 0 rechazos no previstos**. No
se comparan APV_01, APV_02 y APV_10, que dependen de la persona y su calendario.

### Registro de usuarios

- El feedback real tiene dos columnas, `uid;reason`, con el hash en mayúsculas.
  **No trae el uid en claro** (el documento sugería una columna `uids`).
- En el ejemplo, de 569 altas solicitadas se aceptaron 2: la mayoría ya estaban
  registradas (APV_UR_05) o no estaban en el directorio (APV_UR_01).

## Autenticación (guía de consumo de APIs, 29/09/2026)

Lo que la guía establece para una aplicación externa como el conector:

1. BBVA entrega una cuenta de servicio de Google con su clave en un JSON.
2. Con esa clave se pide a Google un token de acceso con el ámbito `email`.
   El ejemplo Java de la guía lo pide delegado a un usuario
   (`createDelegated`); el de Python marca la delegación como opcional.
3. Cada petición al gateway lleva `Authorization: Bearer <token>`,
   `Requestor: <usuario>` y `X-BBVA-AppId: <client id que acompaña a la credencial>`.

Lo que la guía no aclaraba se dejó como configuración y BBVA lo contestó el
06/10/2026, junto con un script de ejemplo en Python (`requests` +
`google-auth`). Los valores por defecto del conector coinciden con lo que
hace ese script:

| Duda | Respuesta de BBVA | Variable | Valor |
|---|---|---|---|
| Valor de `X-BBVA-AppId` | El `client_id` del JSON de la credencial | `AR_APP_ID` | vacío (= `client_id`) |
| Token delegado al Requestor o no | El script usa el token de la propia cuenta de servicio, sin delegar | `AR_DELEGATE_TO_REQUESTOR` | `false` |
| URL de cada entorno | `bbva-gateway.appspot.com` (PRO), `au-bbva-gateway.appspot.com` (AU), `dev-bbva-gateway.appspot.com` (DEV) | `AR_BASE_URL` | AU |
| Nombre del campo del fichero en las subidas | `files={"file": open('activities.csv','rb')}` → campo `file`, `multipart/form-data` | `AR_UPLOAD_FIELD_NAME` | `file` |
| Autorización en el gateway | Las cuentas de servicio ya están autorizadas; lo tramita BBVA | — | — |
| Credencial por entorno | Una distinta para cada entorno. Piden probar en DEV y en AU; las cuentas `@dev.bbva.com` se solicitan desde el ticket de Helix | `AR_CREDENTIALS_FILE` | una ruta por entorno |

Detalles del script de ejemplo:

- Cabecera `requestor` en minúsculas: da igual, las cabeceras HTTP no
  distinguen mayúsculas.
- Lista como catálogo `other_technologies`, retirado según el documento, y
  no lista los históricos de Jira, `countries` ni `hidden_task_types`: el
  script es anterior a la versión 1.16. El conector sincroniza cada catálogo
  por separado, así que uno que no exista no bloquea al resto.
- El nombre del fichero que se sube es indiferente (documento, pág. 23); el
  conector envía `activities_<fecha>.csv`.

Probado de punta a punta en Docker contra un Google y un gateway simulados:
petición de token, las tres cabeceras en descargas y subidas, y subida
`multipart/form-data`.

## Pruebas contra DEV

| Fecha | Cambio | Respuesta de BBVA |
|---|---|---|
| 08/10/2026 | Primera llamada, Requestor `@bbva.com` | 401 "... is not a valid user": en DEV el Requestor tiene que ser `@dev.bbva.com` |
| 03/11/2026 | Requestor `@dev.bbva.com` | 401 "The requestor application cannot be retrieved" en los 8 catálogos (igual con `X-BBVA-AppId` = `client_id` o = `project_id`): faltaba configuración en BBVA |
| 13/11/2026 | BBVA cambia el dominio de DEV a `dev-gag.bbva.com` | **`countries` se descarga** (5 países). Los otros 7 catálogos: 403 "You don't have permissions to invoke this service" |

El `countries` de DEV trae un país con `onhold_margin` vacío; el conector lo
acepta desde la migración V11 (sin margen, la regla correspondiente no se
aplica). Los datos de DEV son de prueba: no incluye España.

## Pendiente de BBVA

1. Permisos en DEV para los otros 7 catálogos (y para subir ficheros y pedir
   feedback): hoy responden 403.
2. Si AU y PRO también cambian de dominio (como DEV a `dev-gag.bbva.com`).
3. Las credenciales de AU y PRO (una por entorno).
4. Confirmación de que tienen los datos de Arktic: GPS `0000417143`,
   Requestor y geografía `ES`.
4. Fecha a partir de la cual la API es la única fuente de imputación para
   Arktic (convivencia con la imputación manual).
5. Qué quiso decir "si están en zip, no hay nodos bloqueados". El lector
   descomprime zip y gzip por si algún catálogo llegara comprimido.
