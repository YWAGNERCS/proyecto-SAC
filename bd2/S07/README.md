# BD2 · S7 · Arquitectura Oracle e instancia

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 7, primera de la Unidad II. Se exploró la instancia Oracle de SGE-JD: memoria,
procesos de fondo, nombres, SYSDBA frente al usuario de la aplicación, estrategia de memoria y alert log. Se hizo
sobre la base de práctica `sge-jd-bd2`, que usa **la misma imagen** que desarrollo y producción (sección 7).

| Orden | Script | Se ejecuta como |
|---|---|---|
| 1 | [S07_01_instancia.sql](S07_01_instancia.sql) | `-Sysdba`: `sqlplus / as sysdba` dentro del contenedor (autenticación del sistema operativo, sin clave) |
| 2 | [S07_02_usuario_aplicacion.sql](S07_02_usuario_aplicacion.sql) | `JDREFRIGERACION` (el usuario del backend) y `-Usuario JD_APP` |
| — | Variables de entorno y alert log | `docker exec sge-jd-bd2 bash -c '…'` (comandos en las secciones 3 y 6) |

```powershell
.\bd2\herramientas\sql.ps1 bd2\S07\S07_01_instancia.sql -Sysdba
.\bd2\herramientas\sql.ps1 bd2\S07\S07_02_usuario_aplicacion.sql
.\bd2\herramientas\sql.ps1 bd2\S07\S07_02_usuario_aplicacion.sql -Usuario JD_APP
```

## 1. Informe de arquitectura de la instancia

| Componente | Valor observado | Evidencia |
|---|---|---|
| Producto y versión | **Oracle AI Database 26ai Free**, versión **23.26.2.0.0**, sobre Oracle Linux 8.10 | `V$INSTANCE.VERSION_FULL`, `ORACLE_HOME`, `/etc/oracle-release` |
| Instancia / base | `FREE` / `FREE`; es CDB con dos PDB: `PDB$SEED` (solo lectura) y `FREEPDB1` (la de SGE-JD) | `V$INSTANCE`, `V$DATABASE`, `V$PDBS` |
| SGA total | **1 529 MB** (Fixed 4,8 + Variable 640 + Buffers 880 + Redo 4,3) | `V$SGA` |
| SGA por componente | Buffer cache 880 · Shared pool 448 · Java pool 128 · Shared IO pool 80 · Streams 32 · Large pool 16 · libre 16 MB | `V$SGAINFO` |
| PGA | Objetivo 512 MB; asignada 249–290 MB; límite duro 2 048 MB | `V$PGASTAT` (`CON_ID = 0`) y `pga_aggregate_limit` |
| Estrategia de memoria | **ASMM**: `sga_target = 1 536 MB` (Oracle reparte la SGA solo); `memory_target = 0` (sin AMM); arrancó con **spfile** | `V$PARAMETER` |
| CPU | `cpu_count = 2`, aunque el equipo tiene **16** núcleos (`nproc`) | `V$PARAMETER`, contenedor |
| Procesos background | 80 filas activas; los clásicos: **PMON, SMON, DBW0, LGWR, CKPT, MMON, MMNL, RECO, LREG, CJQ0** | `V$BGPROCESS WHERE paddr <> '00'` |
| ORACLE_HOME / ORACLE_SID | `/opt/oracle/product/26ai/dbhomeFree` / `FREE` (coincide con `INSTANCE_NAME`) | `docker exec … echo $ORACLE_HOME $ORACLE_SID` |
| Usuarios con SYSDBA | Solo `SYS` (SYSDBA y SYSOPER, cuenta OPEN) | `V$PWFILE_USERS` |
| V$ como usuario de la aplicación | **ORA-00942** con `JDREFRIGERACION` y con `JD_APP` | `S07_02` |
| Modo de archivado | **NOARCHIVELOG** | `V$DATABASE.LOG_MODE` |
| Redo logs | **2 grupos de 10 MB**, un miembro cada uno | `V$LOG` |
| Alert log | `/opt/oracle/diag/rdbms/free/FREE/trace/alert_FREE.log` (1 344 líneas, 6 arranques) | `V$DIAG_INFO` |

**Los límites de la edición Free.** `cpu_count = 2` con 16 núcleos disponibles, y `sga_target` (1,5 GB) más
`pga_aggregate_target` (0,5 GB) suman justo **2 GB**. La edición gratuita limita a 2 CPU y 2 GB de memoria, y la
imagen viene configurada al tope. Los datos de `FREEPDB1` ocupan 0,76 GB, de los 12 GB que permite Free.

## 2. Procesos background contrastados con su responsabilidad

| Proceso | Responsabilidad | Qué significa para SGE-JD |
|---|---|---|
| **LGWR** | Escribe el redo buffer en los redo logs; un `COMMIT` termina cuando LGWR lo escribió | Una venta confirmada ya está a salvo aunque los bloques de datos sigan en memoria |
| **DBW0** | Escribe los bloques modificados del buffer cache a los datafiles, en segundo plano | La venta se escribe "de verdad" en disco después del `COMMIT`, sin hacer esperar a Lucy |
| **CKPT** | Marca hasta dónde DBWn ya escribió, para saber qué redo se puede reutilizar | Es el que falló en llegar a tiempo durante la carga de S4 (hallazgo 1) |
| **SMON** | Recupera la instancia al arrancar después de una caída y limpia segmentos temporales | Si la PC de JD se apaga de golpe, SMON rehace las ventas confirmadas al volver |
| **PMON** | Limpia lo que deja una sesión que se cortó: libera bloqueos y deshace su transacción | Si el backend se cae a mitad de una venta, PMON suelta el bloqueo del producto (BD2 S3) |
| **MMON / MMNL** | Toman métricas y estadísticas de rendimiento | Base de AWR (S10) |
| **CJQ0** | Coordina los jobs programados (`DBMS_SCHEDULER`) | Ejecuta el job automático de estadísticas, el que en la copia estaba atrasado (`STALE_STATS`, S4) |
| **RECO** | Resuelve transacciones distribuidas pendientes | Sin uso en SGE-JD (una sola base) |
| **LREG** | Registra los servicios de la base en el listener | Permite conectarse a `FREEPDB1` por el puerto 1521 |
| **VKTM** | Mantiene la hora interna de la instancia | Aparece en el hallazgo 3 |

Además hay procesos que la guía no menciona: `BG00`–`BG03` (hilos genéricos que se repiten en la lista),
`PXMN` y `P000` (consultas en paralelo), `SMCO` y `W000` (espacio) y `TT00` (transporte de redo). Por eso son
80 filas y no 10.

## 3. Variables de entorno frente a `V$INSTANCE`

```
$ docker exec sge-jd-bd2 bash -c 'echo ORACLE_HOME=$ORACLE_HOME; echo ORACLE_SID=$ORACLE_SID; whoami'
ORACLE_HOME=/opt/oracle/product/26ai/dbhomeFree
ORACLE_SID=FREE
oracle

INSTANCE_NAME  HOST_NAME     VERSION_FULL  STATUS  ARRANQUE          DATABASE_TYPE
FREE           ca006a58ef83  23.26.2.0.0   OPEN    10/11/2026 19:38  SINGLE
```

`ORACLE_SID` y `INSTANCE_NAME` coinciden (`FREE`). El `HOST_NAME` es el identificador del contenedor de Docker,
no el nombre de la PC.

## 4. SYSDBA frente al usuario de la aplicación

**Como SYSDBA** (`sqlplus / as sysdba`): `USER is "SYS"`, `CON_NAME = CDB$ROOT`. Entra a la **raíz** del CDB, no
a la PDB de la aplicación, y ve toda la instancia. No pidió contraseña: el usuario `oracle` del sistema operativo
pertenece al grupo de administradores, y eso basta.

**Como el usuario del backend** (`JDREFRIGERACION`) y como el usuario técnico de S1 (`JD_APP`):

```
SELECT * FROM v$sga
ORA-00942: table or view "SYS"."V_$SGA" does not exist

SELECT instance_name FROM v$instance
ORA-00942: table or view "SYS"."V_$INSTANCE" does not exist
```

El mensaje muestra cómo está construido `V$SGA`: es un **sinónimo público** de la vista `SYS.V_$SGA`. Sin
privilegio sobre esa vista, Oracle responde "no existe" en lugar de "no tienes permiso", para no confirmar ni
siquiera su existencia.

Lo que sí tiene cada usuario:

| Usuario | Roles | Privilegios de sistema |
|---|---|---|
| `JD_APP` (creado en S1) | ninguno | **1**: `CREATE SESSION` |
| `JDREFRIGERACION` (el que usa el backend hoy) | `DB_DEVELOPER_ROLE`, `RESOURCE`, `SODA_APP` | **29**, entre ellos `CREATE JOB`, `CREATE MATERIALIZED VIEW`, `FORCE TRANSACTION`, `DEBUG CONNECT SESSION`, `CREATE MLE`, `EXECUTE DYNAMIC MLE` |

Ninguno de los dos ve la instancia, que es lo correcto. Pero `JDREFRIGERACION` tiene muchos más privilegios de los
que el backend necesita. Se los dio la imagen de Docker al crearlo (`APP_USER`), no una decisión del proyecto.
Es el punto de partida de S8.

## 5. Estrategia de memoria

```
memory_target          0             → sin AMM (Oracle no mueve memoria entre SGA y PGA)
sga_target             1610612736    → ASMM: Oracle reparte la SGA entre sus componentes
sga_max_size           1610612736
pga_aggregate_target   536870912     → la PGA se administra aparte, con objetivo de 512 MB
pga_aggregate_limit    2147483648    → tope duro: "Using default pga_aggregate_limit of 2048 MB" (alert log)
spfile                 /opt/oracle/product/26ai/dbhomeFree/dbs/spfileFREE.ora
```

Con `spfile`, un `ALTER SYSTEM SET … SCOPE=BOTH` sobrevive al reinicio, y uno con `SCOPE=MEMORY` se pierde.

## 6. Alert log

```
$ docker exec sge-jd-bd2 bash -c "grep … /opt/oracle/diag/rdbms/free/FREE/trace/alert_FREE.log"
1344 líneas · 6 arranques · 15 cambios de redo log
  9  Checkpoint not complete
 12  ORA-00800: soft external error, arguments: [Set Priority Failed], [VKTM] / [LGWR] …
  6  ORA-20013: DBMS_QOPATCH ran mostly in non install area
```

## 7. Error o hallazgo

**Hallazgo 1: la base se detuvo durante la carga de S4 ("Checkpoint not complete").** El alert log muestra
esto el 10/11/2026 entre las 19:11:26 y las 19:11:29, que es exactamente la carga de 30 000 ventas de BD2 S4:

```
2026-11-10T19:11:26.232298-05:00
Checkpoint not complete
2026-11-10T19:11:26.472317-05:00
Thread 1 advanced to log sequence 19 (LGWR switch),  current SCN: 3294338
Checkpoint not complete
…
Thread 1 advanced to log sequence 23 (LGWR switch),  current SCN: 3294812
```

Fueron **5 cambios de redo log en 3 segundos y 9 esperas**. La causa está en `V$LOG`: solo hay **2 grupos de
10 MB**. LGWR llenaba uno en menos de un segundo, pasaba al otro, y lo encontraba todavía en uso porque DBW0 no
había terminado de escribir sus bloques (el checkpoint no se completó). Mientras tanto, **ningún `COMMIT` de
ninguna sesión podía avanzar**. Para 2 usuarios vendiendo, 10 MB alcanzan. Para una restauración de un respaldo,
una migración o la carga del primer día, la base se traba por momentos. Y producción usa la misma imagen, con los
mismos 2 × 10 MB. **Propuesta para S9 (almacenamiento):** 3 grupos de 200 MB. Se documenta aquí y se aplica en S9,
que es la sesión de redo, undo y ARCHIVELOG.

**Hallazgo 2: el "23" del nombre ya no es 23ai.** El proyecto documenta "Oracle 23 Free" porque la imagen es
`gvenzl/oracle-free:23-slim`. Pero la instancia reporta `23.26.2.0.0` y su `ORACLE_HOME` dice **26ai**: Oracle
renombró el producto a *Oracle AI Database 26ai* en la actualización 23.26, y la etiqueta `23-slim` ya trae esa
versión. Se comprobó que los tres contenedores (`SGE-JD` de desarrollo, `sge-jd-oracle-prod` y `sge-jd-bd2`)
usan **la misma imagen** (`fbbd3023d5ab`), así que no hay diferencias entre ambientes. El riesgo es a futuro: si
alguien vuelve a descargar `23-slim` en otra PC, puede recibir otra versión. Conviene fijar la imagen por su
digest en `docker-compose.yml` y `compose-prod.yml`, igual que se fijaron las dependencias del frontend.

**Hallazgo 3: el ORA-20013 del alert log no era de SGE-JD.** Al ver ORA-20013 en el alert log, lo primero que
pensé fue en mi `SP_REGISTRAR_VENTA`, porque ese es mi código de "stock insuficiente" (S1 y S3). No lo era. Lo lanza
`SYS.DBMS_QOPATCH` al arrancar, cuando no puede leer la información de parches. El rango de
`RAISE_APPLICATION_ERROR` (-20000 a -20999) **no es exclusivo de la aplicación**: lo usan también paquetes de
Oracle. Por eso `LOG_ERRORES` (S3) guarda el **objeto** que falló y no solo el código.

**Observación (ORA-00800).** VKTM y LGWR intentan subir su prioridad en el sistema operativo y no lo logran
(`Set Priority Failed`, 12 veces, una por proceso en cada uno de los 6 arranques). La causa probable es que el
contenedor de Docker corre sin la capacidad `SYS_NICE`; no lo verifiqué. Es un aviso "soft" y en una PC con
2 usuarios no se nota. En un servidor real convendría revisarlo.

## 8. Relación con ADS y LP2

| Elemento de la instancia | ADS | LP2 |
|---|---|---|
| CDB `FREE` y PDB `FREEPDB1` | C2 (S2): el contenedor "Base de datos Oracle" | `DB_URL=jdbc:oracle:thin:@…/FREEPDB1` |
| LGWR y el `COMMIT` | ACID del monolito modular (S4): una venta en una transacción | `@Transactional` en `VentaService` |
| Límites de Free (2 CPU, 2 GB, 12 GB) | Decisión de despliegue: una PC (ADR-S4-01) | — |
| `JDREFRIGERACION` con 29 privilegios | Seguridad de mínimo privilegio (atributo 2, S1) | El backend se conecta como el **dueño** de las tablas; S8 lo corrige |
| Redo de 2 × 10 MB | Recuperabilidad (RNF-08) | Restaurar un respaldo con `impdp` sufriría lo mismo que la carga de S4 |

## 9. Reflexión

¿Por qué SYSDBA rompe, a propósito, el principio de mínimo privilegio, y por qué esa excepción está bien?

Porque alguien tiene que poder arreglar la base cuando la base misma no funciona. SYSDBA puede arrancar y apagar la
instancia, recuperar después de una caída y cambiar la memoria. Para eso tiene que poder entrar aunque la base
esté cerrada, y por eso se autentica fuera de la base, con el sistema operativo o el archivo de contraseñas. La
excepción está bien porque es **una sola identidad, para una sola tarea, y nunca la usa la aplicación**. En SGE-JD,
SYSDBA solo se usó desde dentro del contenedor, para estos diagnósticos, y el backend nunca lo tuvo. Lo que este
trabajo mostró es que el problema real de privilegios no estaba en SYSDBA, sino en el usuario de todos los días:
`JDREFRIGERACION` tiene 29 privilegios que nadie decidió darle.
