# BD2 · S14 · Monitoreo y diagnóstico

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 14. La pregunta es qué está pasando en la base ahora mismo, sin esperar a que alguien se queje. Se cubren:
- sesiones conectadas;
- un bloqueo real entre dos operaciones del backend;
- objetos inválidos;
- rendimiento en vivo;
- lo que EM Express mostraba.

Todo en la **base de práctica** (`sge-jd-bd2`, puerto 1523), con la imagen completa de S13 (26ai, 23.26.3).

| Orden | Script | Como | Qué hace |
|---|---|---|---|
| 1 | [S14_01_sesiones.sql](S14_01_sesiones.sql) | `-Dba` | `V$SESSION`: quién está conectado |
| 2 | [S14_02_bloqueo.ps1](S14_02_bloqueo.ps1) | PowerShell | Lanza tres sesiones a la vez: A ([S14_02a](S14_02a_cobro_lento.sql)) y B ([S14_02b](S14_02b_anulacion.sql) o [S14_02c](S14_02c_espera_backend.sql)) como `JD_APP`, y C ([S14_02m](S14_02m_monitor.sql)) como SYSTEM, que diagnostica mientras A y B siguen abiertas |
| 3 | [S14_03_verificar.sql](S14_03_verificar.sql) | dueño | Qué cambio sobrevivió (tabla y `VENTA_AUDITORIA`) |
| 5 | [S14_05_objetos_invalidos.sql](S14_05_objetos_invalidos.sql) | `-Sysdba` | `CDB_OBJECTS` y `CDB_REGISTRY` que no están VALID, en la raíz y en FREEPDB1 |
| 6 | [S14_06_recompilar.sql](S14_06_recompilar.sql) | `-Sysdba` | Diagnóstico e intento de recompilar el inválido |
| 7 | [S14_07_rendimiento.sql](S14_07_rendimiento.sql) | `-Dba` | Hit ratio acumulado y alrededor de una carga conocida; esperas de la instancia |
| 8 | [S14_08_em_express.sql](S14_08_em_express.sql) | `-Sysdba` | ¿Hay EM Express? |
| 9 | [S14_09_ash_perfhub.sql](S14_09_ash_perfhub.sql) | `-Sysdba` | ASH y el Performance Hub como HTML |

```powershell
.\bd2\S14\S14_02_bloqueo.ps1 -Venta 182             # B espera sin límite
.\bd2\S14\S14_02_bloqueo.ps1 -Venta 194 -Backend    # B espera como el backend: 10 s y se rinde
```

Las salidas completas quedaron en `bd2/salidas/S14_*.txt`, que no se sube.

## 1. Sesiones activas

Con la base recién encendida y antes de las pruebas (`S14_01`):

| SID | Usuario | Tipo | Programa | Lectura |
|---|---|---|---|---|
| 61 | SYSTEM | USER | `sqlplus` | Yo, haciendo la consulta |
| 169 | SYS | **BACKGROUND** | `oracle (OFSD)` | Tiene usuario, pero es un **proceso de la instancia**, no una persona. Filtrar solo por `USERNAME IS NOT NULL` lo cuenta como si fuera una conexión |
| — | (sin usuario) | BACKGROUND | — | **91** procesos internos (`TYPE = BACKGROUND`, `USERNAME` nulo) |

Durante el bloqueo (sesión C, sección 2) hubo dos sesiones más de `JD_APP`, el usuario del backend (S8). Se distinguen sin ambigüedad por `MODULE`/`ACTION`, que cada una fijó con `DBMS_APPLICATION_INFO`:

| SID | MODULE | ACTION | STATUS | EVENT |
|---|---|---|---|---|
| 61 | SGE-JD cobranza | registrar cobro | ACTIVE | `PL/SQL lock timer` (trabaja, en su `SLEEP`) |
| 224 | SGE-JD ventas | anular venta | ACTIVE | **`enq: TX - row lock contention`**, `BLOCKING_SESSION = 61` |

- Las dos están ACTIVE, pero solo la 224 está **bloqueada**. La diferencia la marcan `EVENT` y `BLOCKING_SESSION`, no `STATUS`.
- Una sesión INACTIVE solo espera al cliente y no retiene ni pide nada.
- **Lección para el proyecto:** el pool de Hikari se conecta siempre como `JD_APP`. Sin `MODULE`, sus conexiones (hasta 10, el valor por defecto de Hikari, porque el proyecto no lo cambia) se verían iguales en `V$SESSION`.

## 2. Bloqueo real entre dos operaciones del negocio

**Escenario.** Las dos sesiones tocan la misma venta (182, a crédito y pendiente de pago).
- **A (cobranza):** bloquea la venta igual que `VentaRepository.bloquear()` (`FOR UPDATE WAIT 10`, del `lock.timeout = 10000`) y la marca PAGADO. Tarda 45 s y al final hace `ROLLBACK`, como un cobro que se rechaza por voucher duplicado.
- **B (ventas):** anula la misma venta con un `UPDATE` simple.

Diagnóstico desde C a las 21:21:49, con A y B abiertas (`S14_02.txt`):

| Vista | Resultado | Qué dice |
|---|---|---|
| `V$SESSION` | 224: `enq: TX - row lock contention`, `BLOCKING_SESSION = 61`, `SECONDS_IN_WAIT = 6` | Quién espera, por qué y a quién |
| `DBA_BLOCKERS` | `HOLDING_SESSION = 61`, `CON_ID = 3` (FREEPDB1) | Quién bloquea |
| `DBA_WAITERS` | `224 ← 61`, `Transaction`, `Exclusive` / `Exclusive` | El par completo y el modo |
| `V$SESSION` + `V$SQL` + `DBA_OBJECTS` | `VENTAS` (objeto 73199), fila 11; `UPDATE … SET estado = 'ANULADA' … WHERE id = 182` | **Qué** quiere hacer la que espera, y sobre qué fila |
| `V$LOCK` (TX) | 61: `LMODE 6`, `BLOCK 1`; 224: `REQUEST 6` | El candado de la transacción, sin la vista de alto nivel |

**Resolución.** No se mató ninguna sesión. A terminó y revirtió (`ROLLBACK` a las 21:22:25.13), y B siguió sola en ese mismo segundo. `S14_03` confirma qué quedó:

| Venta 182 | Antes | Cambio de A (cobranza) | Cambio de B (anulación) | Resultado |
|---|---|---|---|---|
| `estado` | REGISTRADA | — | ANULADA | **ANULADA** (sobrevivió) |
| `estado_pago` | PENDIENTE | PAGADO | — | **PENDIENTE** (se revirtió) |
| `VENTA_AUDITORIA` | — | — | `UPDATE REGISTRADA → ANULADA`, `JD_APP`, **21:22:25** | B esperó ~42 s y se aplicó en el instante en que A soltó la fila |

Matar la 61 habría dado el mismo resultado, pero solo porque A iba a revertir. Si A hubiera sido un cobro bueno a punto de confirmarse, el `KILL` lo habría perdido. Por eso se diagnostica primero: `V$SQL` dice qué está haciendo cada una.

### 2.1 El mismo bloqueo, como lo vive el backend

El backend nunca hace un `UPDATE` sin bloquear antes: `anular()` llama a `bloquear()`, que espera **10 s** como máximo. Se repitió con la venta 194 y B usó `FOR UPDATE WAIT 10` (`S14_04.txt`):

| Hora | Hecho |
|---|---|
| 21:22:57.01 | A bloquea la venta 194 |
| 21:23:00.08 | B pide la misma fila; el monitor la ve en `enq: TX` con `BLOCKING_SESSION = 61`, igual que antes |
| **21:23:10.11** | B **se rinde a los 10,03 s**: `ORA-00054: Failed to acquire a lock … ORA-40097: Failure to acquire row lock, currently held by transaction ID 9.5.660` |
| 21:23:27.03 | A revierte; la venta sigue REGISTRADA y PENDIENTE, sin fila de auditoría (el trigger de S2 también se revirtió) |

- **Hallazgo, el mismo de S3:** Oracle 23 responde ORA-00054 y no ORA-30006 cuando vence el `WAIT`. El mensaje de 26ai ya trae el ID de la transacción que bloquea, lo que en 19c había que buscar en `V$LOCK`.
- En el backend, `GlobalExceptionHandler` (líneas 90–96) atrapa las excepciones de bloqueo de Spring y JPA y responde **409**: "Otro usuario está registrando una operación con los mismos productos o documentos". Lo verifiqué leyendo el código, no ejecutando el backend en esta sesión.
- **En producción, entonces, una sesión del backend nunca queda colgada:** a los 10 s el usuario recibe un mensaje.
- El bloqueo sin límite de la sección 2 solo puede venir de **fuera** del backend: un DBA o un script en SQL Developer que deja una transacción abierta.

## 3. Objetos inválidos

`S14_05`, sobre toda la instancia (`CDB_OBJECTS`, raíz y FREEPDB1):

| Contenedor | Dueño | Objeto | Tipo | Estado |
|---|---|---|---|---|
| FREEPDB1 | PUBLIC | `SDO_AGGR_POINTAPPEND` | SYNONYM | **INVALID** |

**Es el único**, y es el mismo que encontró la guía. Los objetos del proyecto (`JDREFRIGERACION`) están **todos VALID**: 37 tablas, 58 particiones y 9 subparticiones (S11), 67 índices, 2 funciones, 2 procedimientos, 3 triggers y 29 secuencias.

Diagnóstico (`S14_06`):
- `DBA_SYNONYMS` no lo lista y `SYS.SYN$` no tiene su definición. Es un **objeto sin destino**: queda su fila en el diccionario, pero no a qué apunta.
- `MDSYS` (Oracle Spatial) tiene **0 objetos** y nada depende del sinónimo (`DBA_DEPENDENCIES`: 0).
- Fue creado el **28/04/2026**, la fecha de la encarnación 1 (S13, `LIST INCARNATION`). Viene de cuando Oracle armó la imagen y quitó Spatial; no lo creamos nosotros.
- **Intento real de recompilar:** `ALTER PUBLIC SYNONYM sdo_aggr_pointappend COMPILE` → **ORA-00942: table or view does not exist**. Sigue INVALID.

Decisión: **no se borra.** Es un objeto de Oracle en un esquema público, ningún objeto de SGE-JD lo usa, y borrar a mano filas del diccionario es peor que dejarlas. La consulta de monitoreo lo excluye por nombre y avisa si aparece **cualquier otro**.

`CDB_REGISTRY` muestra además el componente **XDB INVALID** en la raíz y en FREEPDB1 (ya visto en S9), y JAVAVM, CATJAVA y XML en REMOVED. Es parte del diagnóstico de EM Express (sección 5).

## 4. Rendimiento en vivo

`S14_07`, con la instancia encendida a las 21:20:36:

| Medida | Lecturas lógicas | Físicas | Hit ratio | Parse duro | Tiempo |
|---|---|---|---|---|---|
| Acumulado desde el arranque | 51 076 | 1 641 | **96,79 %** | — | — |
| Reporte de rentabilidad (5 tablas), 1 vez con caché vacía (`FLUSH BUFFER_CACHE`) | 1 367 | 837 | **38,77 %** | 1 de 15 | 110 ms |
| El mismo reporte, 19 veces más | 15 124 | **0** | **100 %** | 0 | 580 ms (31 ms cada una) |

- El 96,79 % no dice nada por sí solo. Es el promedio entre el primer uso (39 %) y los siguientes (100 %).
- Lo que sí dice: el esquema de ventas entero cabe en el buffer cache (~880 MB de la SGA de 1,5 GB, S7). Después de la primera lectura, el reporte nunca vuelve a disco.
- Subir la memoria no mejoraría nada: el 100 % ya está.

**El indicador que sí importa hoy está en `V$SYSTEM_EVENT`.** Las esperas de la instancia desde el arranque, sin contar las ociosas:

| Evento | Esperas | Segundos |
|---|---|---|
| **`enq: TX - row lock contention`** | 2 | **52,1** |
| `db file sequential read` | 1 795 | 0,5 |
| `db file scattered read` | 190 | 0,3 |
| `log file sync` | 20 | 0,0 |

- El tiempo de la instancia se fue en **esperar al otro usuario**, no en disco ni en CPU.
- Los 52,1 s son los dos bloqueos de la sección 2 (~42 s y 10 s).
- Un tablero que solo mire el hit ratio habría marcado "saludable" durante todo el incidente.

## 5. Enterprise Manager Express: no existe en esta versión

`S14_08`:
- El puerto HTTPS de XDB está configurado en **5500**, y el listener lo publica (`PROTOCOL=tcps … PORT=5500 … Presentation=HTTP`).
- Aun así, `curl -k https://localhost:5500/em` dentro del contenedor falla en el saludo TLS (`SSL_ERROR_SYSCALL`, error 35). XDB está INVALID.
- La causa de fondo: **EM Express quedó desoportado desde Oracle 23ai**. Ya estaba obsoleto desde 21c, y Oracle recomienda Cloud Control, el servicio Database Management de OCI o SQL Developer. Quedan restos en el `ORACLE_HOME` (`execemx.sql`, `emremove.sql`), pero no la aplicación.
- La guía da por hecho que EM Express existe; en S9 ya no se pudo habilitar.

Lo que EM Express mostraba, sacado de la misma fuente (`S14_09`):

| Evidencia | Dónde se confirmó | Resultado |
|---|---|---|
| Sesión bloqueada y su evento | **ASH** (`V$ACTIVE_SESSION_HISTORY`, una muestra por segundo) | SID 224, módulo "SGE-JD ventas", `enq: TX - row lock contention`, bloqueada por la 61: **52 muestras** entre 21:21:44 y 21:23:10. Es el mismo número que `V$SYSTEM_EVENT`, visto desde otra vista |
| Sesiones, esperas y actividad | **Performance Hub** (`DBMS_PERF.REPORT_PERFHUB`, `type => 'ACTIVE'`), la misma pantalla de EM Express exportada como HTML | `bd2/salidas/perfhub_s14.html` (63 KB), generado. Se abre en Chrome |

**Hallazgo:** el HTML del Performance Hub **no trae su propio visor**. Carga el JavaScript de `https://download.oracle.com/otn_software/omx/…` al abrirse. Sin internet, o si ese sitio cae, el informe sale en blanco. Es el caso de AWS en pequeño: la vista bonita depende de un servicio externo. Las consultas a `V$` y ASH de este README no dependen de nada fuera de la instancia.

Nota de licencia: ASH y el Performance Hub son del Diagnostics Pack. En la edición Free se pueden usar; en Enterprise Edition se licencian aparte (igual que AWR en S10).

## 6. Reporte de monitoreo

| Área | Hallazgo | Acción |
|---|---|---|
| Sesiones | 1 de usuario + 1 de SYS que es proceso de fondo (OFSD) + 91 internos; durante la prueba, 2 de `JD_APP` que solo se distinguen por `MODULE` | Proponer que el backend fije `MODULE`/`ACTION` por operación (integración con LP2) |
| Bloqueos | Bloqueo real cobranza contra anulación sobre la venta 182, diagnosticado con `DBA_BLOCKERS`/`DBA_WAITERS`, `V$SQL` y `V$LOCK`. Con la espera del backend, B se rinde a los 10 s con ORA-00054 | Sin `KILL`: A revirtió y la anulación sobrevivió (verificado en la tabla y la auditoría). El backend ya responde 409 a los 10 s |
| Objetos | 0 inválidos del proyecto; 1 sinónimo de Oracle (`SDO_AGGR_POINTAPPEND`) sin destino desde la creación de la imagen; XDB INVALID | Recompilación intentada: ORA-00942. Se documenta y se excluye por nombre del monitoreo |
| Rendimiento | Hit ratio de 96,79 % acumulado, 38,77 % frío y 100 % caliente; la espera dominante fue `enq: TX` con 52,1 s | Ninguna en memoria. Vigilar las esperas por bloqueo, no el hit ratio |
| Herramienta visual | EM Express desoportado en 23ai/26ai; Performance Hub como HTML que depende del CDN de Oracle | Usar las consultas de este README como monitoreo base; el HTML queda como complemento |

## 7. Errores y hallazgos de la sesión

1. **Error propio: el orquestador perdió la salida de B.** `S14_02_bloqueo.ps1` se escribió en UTF-8 sin BOM, y PowerShell 5.1 lo lee en ANSI. Los bytes de la "Ó" de "SESIÓN" incluyen 0x93, que en Windows-1252 es `“`, y PowerShell lo toma como **comilla doble**. El texto se cerró antes de tiempo y la línea que mostraba la salida de B no se ejecutó. La evidencia de B salió de la base (`VENTA_AUDITORIA` a las 21:22:25), que es más confiable que la consola. Corrección: textos solo ASCII en los `.ps1` escritos desde bash.
2. **ASH vacío desde la raíz.** La consulta de ASH dio "no rows" al correr en `CDB$ROOT`: `JD_APP` es un usuario **local** de FREEPDB1 y no existe en `DBA_USERS` de la raíz. Dentro del PDB dio las 52 muestras.
3. **Ruido en el arranque:** `ORA-20013: DBMS_QOPATCH ran mostly in non install area` en el log de cada arranque. Es la consulta interna del inventario de parches, que no corre bien en el contenedor; no afecta a la base. Un monitoreo del log tiene que saber que este error es "normal" para no gritar en cada arranque.

## 8. Reflexión

- Una herramienta de monitoreo que depende de lo que vigila se cae junto con lo que vigila, justo cuando más se la necesita. Le pasó al panel de AWS en 2017.
- Las vistas `V$` leen la SGA de la misma instancia: mientras haya una sesión de SQL*Plus, hay diagnóstico, sin servidor web, sin agente y sin internet.
- Aquí se vio al revés también. EM Express ya no existe, y el Performance Hub que lo reemplaza **necesita el CDN de Oracle para dibujarse**. Las 52 muestras del bloqueo, en cambio, salieron de una consulta.
- La otra lección: el hit ratio de 96,79 % estuvo "verde" mientras la única espera seria de la instancia era una anulación trabada 42 s. Monitorear es elegir la métrica que responde la pregunta, no la más famosa.

## 9. Qué pasa a la integración con LP2

| Tema | Propuesta | Estado |
|---|---|---|
| `MODULE`/`ACTION` en el backend | Fijar el módulo de Spring Modulith y la operación en cada conexión (por ejemplo con `DBMS_APPLICATION_INFO` en un aspecto, o el `clientInfo` de JDBC) para que `V$SESSION` y ASH digan qué pantalla bloquea a cuál | Propuesto |
| Consulta de monitoreo diaria | `S14_05` (objetos inválidos, excluyendo `SDO_AGGR_POINTAPPEND`) y las esperas por bloqueo de `S14_07`, junto al respaldo diario | Propuesto |
