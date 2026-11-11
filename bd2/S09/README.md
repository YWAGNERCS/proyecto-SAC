# BD2 · S9 · Almacenamiento, seguridad y auditoría

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 9: estructura física explícita (tablespace propio y redo de tamaño correcto) y
**auditoría que el propio usuario de la aplicación no puede apagar**. Se hizo en la base de práctica, con el
modelo de seguridad de S8 ya aplicado (`JD_APP`, `JD_REPORTES` y sus roles).

| Orden | Script | Se ejecuta como |
|---|---|---|
| 1 | [S09_01_tablespace.sql](S09_01_tablespace.sql) | SYSTEM (`-Dba`) |
| 2 | [S09_02_redo_undo.sql](S09_02_redo_undo.sql) | SYSDBA (`-Sysdba`): los redo logs son de la instancia, no de la PDB |
| 3 | [S09_03_auditoria.sql](S09_03_auditoria.sql) | SYSTEM |
| 4 | [S09_04_evidencia_jd_app.sql](S09_04_evidencia_jd_app.sql) + una conexión con clave incorrecta | `JD_APP` |
| 5 | [S09_05_rastro.sql](S09_05_rastro.sql) | SYSTEM |

## 1. Tablespace propio para las ventas

```sql
CREATE TABLESPACE ts_sgejd_ventas
  DATAFILE '/opt/oracle/oradata/FREE/FREEPDB1/ts_sgejd_ventas01.dbf' SIZE 100M
  AUTOEXTEND ON NEXT 50M MAXSIZE 2G;
```
```
TABLESPACE_NAME  FILE_NAME                                                  MB  AUT  MAX_MB
TS_SGEJD_VENTAS  /opt/oracle/oradata/FREE/FREEPDB1/ts_sgejd_ventas01.dbf   100  YES   2048
```

**Por qué la ruta es explícita.** `db_create_file_dest` está vacío en esta instancia, y el único directorio que
`compose-bd2.yml` monta como volumen es `/opt/oracle/oradata`. Un datafile fuera de ahí quedaría en la capa
efímera del contenedor y se perdería al recrearlo, mientras el resto de la base sobrevive.

**Mover `VENTAS` y `DETALLE_VENTA`**, en orden:

```
ALTER TABLE jdrefrigeracion.ventas MOVE TABLESPACE ts_sgejd_ventas
ORA-01950: The user JDREFRIGERACION has insufficient quota on tablespace TS_SGEJD_VENTAS.
                                     ← ni el dueño puede tener segmentos donde no tiene cuota
ALTER USER jdrefrigeracion QUOTA UNLIMITED ON ts_sgejd_ventas;      User altered.
ALTER TABLE jdrefrigeracion.ventas        MOVE TABLESPACE …;         Table altered.
ALTER TABLE jdrefrigeracion.detalle_venta MOVE TABLESPACE …;         Table altered.

TABLE_NAME     INDEX_NAME          STATUS    TABLESPACE_NAME
DETALLE_VENTA  SYS_C008926         UNUSABLE  USERS
VENTAS         SYS_C008839         UNUSABLE  USERS
VENTAS         UX_VENTAS_PROFORMA  UNUSABLE  USERS
```

Mover una tabla cambia el `ROWID` de cada fila, y los índices quedan apuntando a direcciones que ya no existen.
Lo que vería la aplicación en ese momento:

```
SELECT /*+ INDEX(v) */ id, fecha_venta FROM jdrefrigeracion.ventas v WHERE id = 1
ORA-01502: index 'JDREFRIGERACION.SYS_C008839' or partition of such index is in unusable state

INSERT INTO jdrefrigeracion.ventas (…) VALUES (…)
ORA-01502: index 'JDREFRIGERACION.SYS_C008839' or partition of such index is in unusable state
```

El INSERT falla porque el índice de la clave primaria es el que garantiza que el ID no se repita: con él
inutilizable, Oracle no puede aceptar filas nuevas. **Mientras los índices estén así, Lucy no puede registrar
ninguna venta.** Después del `REBUILD TABLESPACE ts_sgejd_ventas`:

```
TABLE_NAME     INDEX_NAME          STATUS  TABLESPACE_NAME
DETALLE_VENTA  SYS_C008926         VALID   TS_SGEJD_VENTAS
VENTAS         SYS_C008839         VALID   TS_SGEJD_VENTAS
VENTAS         UX_VENTAS_PROFORMA  VALID   TS_SGEJD_VENTAS

SEGMENT_NAME        SEGMENT_TYPE  TABLESPACE_NAME  EXTENTS  KB
DETALLE_VENTA       TABLE         TS_SGEJD_VENTAS        2  128
VENTAS              TABLE         TS_SGEJD_VENTAS        1   64
SYS_C008839         INDEX         TS_SGEJD_VENTAS        1   64
…
ID FECHA_VENTA  →  1 01-NOV-25     (la misma consulta que antes falló)
```

`REBUILD` sin `TABLESPACE` deja el índice donde estaba (`USERS`). Para que tabla e índice queden juntos hay que
indicarlo. En producción convendría `ALTER TABLE … MOVE ONLINE`, que mantiene los índices utilizables durante el
movimiento. Aquí se usó el `MOVE` simple a propósito, para ver el problema.

## 2. Redo, undo y modo de archivado

**Antes** (lo mismo que encontró S7):

```
GROUP#  SEQUENCE#  MB  MEMBERS  STATUS    ARC      MEMBER
     1         17  10        1  CURRENT   NO       /opt/oracle/oradata/FREE/redo01.log
     2         16  10        1  INACTIVE  NO       /opt/oracle/oradata/FREE/redo02.log

UNDO: UNDOTBS1 en CDB$ROOT y otro UNDOTBS1 en FREEPDB1 (undo local por PDB); undo_management AUTO; undo_retention 900 s

LOG_MODE      FORCE_LOGGING     Database log mode       No Archive Mode
NOARCHIVELOG  NO                Automatic archival      Disabled
                                Archive destination     /opt/oracle/product/26ai/dbhomeFree/dbs/arch
```

**Cambio aplicado (propuesto en S7).** Se agregan 3 grupos de 200 MB, se cambia de log, se fuerza el
checkpoint y se retiran los de 10 MB cuando quedan `INACTIVE`:

```
GROUP#  MB  STATUS        →   GROUP#  SEQUENCE#   MB  MEMBERS  STATUS
     1  10  INACTIVE               3         18  200        1  INACTIVE
     2  10  INACTIVE               4         19  200        1  CURRENT
     3 200  INACTIVE               5          0  200        1  UNUSED
     4 200  CURRENT
     5 200  UNUSED
```

Con 200 MB por grupo, la carga de 30 000 ventas de S4, que llenó 5 logs de 10 MB en 3 segundos, cabría en uno
solo. Queda pendiente **multiplexar** (dos miembros por grupo, en discos distintos), que no tiene sentido en una
PC con un solo disco.

**ARCHIVELOG: procedimiento documentado, no ejecutado** (se activa en S13, la sesión de RMAN):

```sql
-- 1) Destino DENTRO del volumen persistente (el de fábrica, …/dbs/arch, está fuera: hallazgo 2)
ALTER SYSTEM SET db_recovery_file_dest_size = 4G SCOPE = BOTH;
ALTER SYSTEM SET db_recovery_file_dest = '/opt/oracle/oradata/fra' SCOPE = BOTH;   -- crear la carpeta antes
-- 2) Reiniciar en MOUNT: cierra todas las sesiones (el backend se cae mientras dura)
SHUTDOWN IMMEDIATE
STARTUP MOUNT
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;
ALTER PLUGGABLE DATABASE ALL OPEN;   -- FREEPDB1 no siempre abre sola
-- 3) Verificar
ARCHIVE LOG LIST
ALTER SYSTEM SWITCH LOGFILE;         -- debe aparecer un archivo en la FRA
```

## 3. Auditoría unificada

La auditoría tradicional ya no existe en esta versión:

```
AUDIT INSERT, UPDATE, DELETE ON jdrefrigeracion.ventas BY ACCESS
ORA-46401: No new traditional AUDIT configuration is allowed. Traditional auditing is desupported, and you should
use unified auditing in its place.
```

**Política de sentencias**, sobre las tres tablas que mueven dinero, para cualquier usuario:

```sql
CREATE AUDIT POLICY pol_sgejd_ventas
  ACTIONS INSERT ON jdrefrigeracion.ventas,        UPDATE ON jdrefrigeracion.ventas,        DELETE ON jdrefrigeracion.ventas,
          INSERT ON jdrefrigeracion.detalle_venta, UPDATE ON jdrefrigeracion.detalle_venta, DELETE ON jdrefrigeracion.detalle_venta,
          INSERT ON jdrefrigeracion.cobros,        UPDATE ON jdrefrigeracion.cobros,        DELETE ON jdrefrigeracion.cobros;
AUDIT POLICY pol_sgejd_ventas;
```

**Política de acceso**, sobre los usuarios de la aplicación, más los intentos fallidos de cualquiera:

```sql
CREATE AUDIT POLICY pol_sgejd_sesiones ACTIONS LOGON, LOGOFF;
AUDIT POLICY pol_sgejd_sesiones BY jd_app, jd_reportes;
AUDIT POLICY ora_logon_failures;
```
```
POLICY_NAME         ENABLED_OPTION  ENTITY_NAME   SUC  FAI        POLICY_NAME       AUDIT_OPTION  OBJECT_NAME
ORA_LOGON_FAILURES  BY USER         ALL USERS     YES  YES        POL_SGEJD_VENTAS  DELETE        COBROS
POL_SGEJD_SESIONES  BY USER         JD_APP        YES  YES        POL_SGEJD_VENTAS  INSERT        COBROS
POL_SGEJD_SESIONES  BY USER         JD_REPORTES   YES  YES        …  (9 acciones, 3 tablas)
POL_SGEJD_VENTAS    BY USER         ALL USERS     YES  YES        POL_SGEJD_VENTAS  UPDATE        VENTAS
```

## 4. Evidencia real en `UNIFIED_AUDIT_TRAIL`

`JD_APP` hace algo permitido y algo denegado, intenta apagar la auditoría y borrar el rastro. Después alguien se
conecta con una clave equivocada:

```
JD_APP> UPDATE ventas SET estado_pago = estado_pago WHERE id = …;   1 row updated.  ROLLBACK
JD_APP> DELETE FROM detalle_venta WHERE id = -1;                     ORA-41900: missing DELETE privilege …
JD_APP> NOAUDIT POLICY pol_sgejd_ventas;                             ORA-01031: insufficient privileges
JD_APP> NOAUDIT POLICY pol_sgejd_sesiones BY jd_app;                 ORA-01031: insufficient privileges
JD_APP> SELECT COUNT(*) FROM unified_audit_trail;                    ORA-00942: … "AUDSYS"."UNIFIED_AUDIT_TRAIL" does not exist
JD_APP> DELETE FROM audsys.aud$unified;                              ORA-00942: table or view does not exist
$ sqlplus jd_app/clave_incorrecta_s9@…                               ORA-01017: invalid credential or not authorized; logon denied
```

Lo que quedó registrado, consultado como SYSTEM:

```
HORA      DBUSERNAME  ACTION_NAME  OBJETO                          RETURN_CODE  POLITICA            PROGRAMA
20:37:48  JD_APP      LOGON        .                                         0  ORA_LOGON_FAILURES, sqlplus@…
20:37:48  JD_APP      UPDATE       JDREFRIGERACION.VENTAS                    0  POL_SGEJD_VENTAS    sqlplus@…
20:37:48  JD_APP      DELETE       JDREFRIGERACION.DETALLE_VENTA          2004  POL_SGEJD_VENTAS    sqlplus@…
20:37:48  JD_APP      NOAUDIT      SYS.POL_SGEJD_VENTAS                   1031  ORA$MANDATORY       sqlplus@…
20:37:48  JD_APP      NOAUDIT      SYS.POL_SGEJD_SESIONES                 1031  ORA$MANDATORY       sqlplus@…
20:37:48  JD_APP      LOGOFF       .                                         0  POL_SGEJD_SESIONES  sqlplus@…
20:37:50  JD_APP      LOGON        .                                      1017  ORA_LOGON_FAILURES, sqlplus@…
```

- El **UPDATE permitido** quedó registrado aunque se deshizo con ROLLBACK: la auditoría registra la sentencia,
  no el resultado de la transacción.
- El **DELETE denegado** quedó registrado con su `RETURN_CODE` (hallazgo 3).
- El **intento de apagar la auditoría** quedó registrado por `ORA$MANDATORY`, la auditoría obligatoria de Oracle
  que nadie puede desactivar: quien intenta tocar la auditoría siempre deja rastro.
- La **conexión fallida** quedó con `RETURN_CODE = 1017`, aunque la sesión nunca llegó a existir.
- `JD_APP` **no puede ni leer** el rastro (ORA-00942).

## 5. Enterprise Manager Express: no disponible en esta instancia

```
EXEC DBMS_XDB_CONFIG.SETHTTPSPORT(5500);   →  PL/SQL procedure successfully completed.
SELECT DBMS_XDB_CONFIG.GETHTTPSPORT …      →  5500
lsnrctl status  →  (PROTOCOL=tcps)(HOST=bf2268a838d9)(PORT=5500)(Presentation=HTTP)   ← el listener abre el puerto
curl -k https://172.23.0.2:5500/em/        →  OpenSSL SSL_connect: SSL_ERROR_SYSCALL      ← y cierra la conexión
DBA_REGISTRY: XDB  Oracle XML Database  INVALID
```

El puerto se configura y el listener lo abre, pero el servidor corta la conexión en el saludo TLS: no hay
aplicación detrás. Hay dos causas que coinciden. **Oracle declaró EM Express sin soporte desde 23ai**, y esta
instancia es 26ai (23.26, ver S7). Además, la imagen es la variante **`23-slim`**, que retira componentes, y deja
XDB en `INVALID`, el componente que servía EM Express. Por eso no se publicó el puerto 5500 en `compose-bd2.yml`:
no habría nada que ver.

**Alternativa.** La misma información sale del diccionario de datos, como se mostró en las secciones 1 a 4
(`DBA_DATA_FILES`, `DBA_SEGMENTS`, `V$LOG`, `AUDIT_UNIFIED_ENABLED_POLICIES`, `UNIFIED_AUDIT_TRAIL`). Para pantallas
se puede usar un cliente gráfico (SQL Developer o DBeaver, conexión `localhost:1523/FREEPDB1` como SYSTEM):
*Tablespaces* y *Audit Policies*. La consola web que reemplaza a EM Express es *Database Actions* (ORDS), que no
viene en esta imagen.

## 6. Relación con ADS y LP2

| BD2 | ADS | LP2 |
|---|---|---|
| `TS_SGEJD_VENTAS` | Contenedor Oracle (C2): las ventas, separadas físicamente del resto | Sin cambios: JPA no sabe en qué tablespace está una tabla |
| Índices `UNUSABLE` tras un `MOVE` | Disponibilidad: una tarea de mantenimiento puede frenar las ventas | `POST /api/v1/admin/ventas/directa` respondería 500 con ORA-01502 hasta el `REBUILD` |
| Redo de 3 × 200 MB | Recuperabilidad (RNF-08) | Una restauración con `impdp` ya no se traba |
| `POL_SGEJD_VENTAS` | Auditabilidad (atributo 3, S1) | Cada venta y cada cobro que registra el backend queda auditado, sin cambiar código |
| `ORA$MANDATORY` y `JD_APP` sin acceso al rastro | Seguridad (S8) | La conexión del backend no puede apagar ni borrar su propia auditoría |

## 7. Error o hallazgo

**Hallazgo 1: la sintaxis de la guía auditaba toda la base.** La primera política se escribió como en la guía:

```sql
ACTIONS INSERT, UPDATE, DELETE ON jdrefrigeracion.ventas, INSERT, UPDATE, DELETE ON jdrefrigeracion.detalle_venta, …
```

`AUDIT_UNIFIED_POLICIES` mostró esto:

```
POL_SGEJD_VENTAS  DELETE  COBROS
POL_SGEJD_VENTAS  DELETE  DETALLE_VENTA
POL_SGEJD_VENTAS  INSERT  NONE        ← INSERT sobre CUALQUIER objeto
POL_SGEJD_VENTAS  UPDATE  NONE        ← UPDATE sobre CUALQUIER objeto
POL_SGEJD_VENTAS  DELETE  VENTAS
```

El `ON tabla` se aplica solo a la acción que tiene inmediatamente al lado. `INSERT, UPDATE, DELETE ON ventas`
significa "INSERT en todo, UPDATE en todo, DELETE en ventas". La política habría auditado cada INSERT y cada
UPDATE de las 31 tablas: correos, historial, series. Habría llenado `AUDSYS` de ruido y escondido justo lo que
importa. **Corrección:** cada acción con su propio `ON`. Ahora son 9 filas, todas con su tabla.

**Hallazgo 2: el destino de archivado de fábrica está fuera del volumen.** `ARCHIVE LOG LIST` muestra
`/opt/oracle/product/26ai/dbhomeFree/dbs/arch`, que está dentro del `ORACLE_HOME` y no en `/opt/oracle/oradata`.
Activar ARCHIVELOG sin cambiarlo dejaría los archivos de redo archivados en la capa efímera del contenedor. El
día que se recree el contenedor, por ejemplo al actualizar la imagen, se perderían, y con ellos la posibilidad de
recuperar hasta un punto en el tiempo. El procedimiento de la sección 2 empieza por fijar
`db_recovery_file_dest` dentro del volumen.

**Hallazgo 3: el rastro no guarda el código que vio el usuario.** `JD_APP` recibió ORA-41900 al intentar el
DELETE, pero `UNIFIED_AUDIT_TRAIL` lo registró con `RETURN_CODE = 2004` (ORA-02004, "security violation"). Quien
busque los accesos denegados con `WHERE return_code = 41900`, o con 1031 como dice la guía, no encontraría
ninguno. La consulta correcta para "intentos rechazados" es `return_code <> 0`.

**Hallazgo 4: EM Express ya no existe en esta versión** (sección 5). La guía lo da por disponible. En 26ai con la
imagen *slim*, el puerto se configura pero no responde.

## 8. Reflexión

¿Por qué una auditoría que el propio usuario de la aplicación pudiera desactivar no sería evidencia confiable?
Relación con el caso del Banco de Bangladesh.

En el robo al Banco de Bangladesh, los atacantes usaron credenciales legítimas y además manipularon el software
que registraba e imprimía las confirmaciones, para que nadie viera las transferencias a tiempo. El registro
existía, pero lo controlaba la misma identidad que cometía el fraude. Si `JD_APP` pudiera hacer `NOAUDIT`, quien
tomara la conexión del backend podría apagar la auditoría, borrar ventas o cobros, y volver a encenderla, sin dejar
huella. La auditoría solo vale como evidencia si la identidad auditada no la controla. En SGE-JD eso se cumple en
tres capas, comprobadas en la sección 4:

1. `JD_APP` no tiene `AUDIT SYSTEM` y su `NOAUDIT` falla.
2. Ese intento queda registrado igual, por `ORA$MANDATORY`.
3. `JD_APP` ni siquiera puede leer el rastro, y menos borrarlo.
