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
| Documentación de firma | Es la guía de consumo de APIs de platform.bbva.com. Exige iniciar sesión con cuenta de BBVA | **Pendiente**: hay que abrirla con una cuenta de contratista y pasar su contenido |
| Entornos | DEV `dev-bbva-gateway.appspot.com` (cuentas @dev.bbva.com), AU `au-bbva-gateway.appspot.com` y PRO `bbva-gateway.appspot.com` (cuentas @bbva.com) | En `.env.example` |
| Conectividad | Accesible desde Internet | El piloto se puede hacer desde un portátil |
| Requestor | Una o varias cuentas `xxxxx.yyyyy.contractor@bbva.com` | Sin cambios |
| Error 400 | `{"code":400,"error_code":"INVALID_CONFIGURATION","message":"[API VENDORS] Feedback file not found."}` | Un feedback que aún no existe devuelve 400, no 404: se trata como "todavía no disponible" |
| Procesamiento N+1 | No corre en fin de semana: si el 19 cae en sábado, el último día de envío es el 18 | `ModifiablePeriodPolicy` adelanta el corte al viernes |
| Festivos | Activity Report solo conoce los nacionales. Los locales se envían como día no imputable | Era lo implementado |
| Herencia en el árbol | Las exigencias del nivel superior se aplican a las hojas | Era lo implementado |
| Identificador de persona | SHA256 del código tipo XE123456 en mayúsculas | Era lo implementado |
| Nombre del campo multipart | No contestado | Se usa `file`; se comprobará en DEV |
| Códigos GPS | No contestado | Pendiente |
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
- El envío del 24/09 tiene un feedback llamado `..._20260925_feedback.csv`: **la
  fecha del fichero es la de procesamiento**. Como no está claro con qué fecha
  hay que pedirlo, `FeedbackLocator` prueba la de envío y las siguientes y
  acepta el fichero cuyo contenido corresponde a lo enviado.
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

## Pendiente de BBVA

1. Contenido de la guía de firma (requiere cuenta de BBVA).
2. Códigos GPS de Arktic y geografías registradas.
3. Si el fichero enviado un viernes se procesa el sábado o el lunes.
4. Con qué fecha se pide el feedback (resuelto en el código probando ambas).
5. Nombre del campo multipart.
6. Fecha a partir de la cual la API es la única fuente de imputación para Arktic.
7. Qué quiso decir "si están en zip, no hay nodos bloqueados". El lector
   descomprime zip y gzip por si algún catálogo llegara comprimido.
