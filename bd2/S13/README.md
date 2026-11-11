# BD2 · S13 · Backup y recovery con RMAN

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 13. Pasos:
- Activar ARCHIVELOG.
- Hacer un backup incremental de nivel 0 y otro de nivel 1.
- Simular un incidente real sobre una tabla del proyecto.
- Recuperar la base al minuto anterior al incidente (PITR) y medir RTO y RPO.
- Comparar con el respaldo lógico (Data Pump) que el proyecto ya usa.

Todo se hizo en la **base de práctica** (`sge-jd-bd2`, puerto 1523). El 4200 y el 4300 no se tocaron.

| Orden | Script | Como | Qué hace |
|---|---|---|---|
| 1 | [S13_01_zona_horaria.sql](S13_01_zona_horaria.sql) | `-Sysdba` | Diagnóstico: archivo de zonas horarias cargado y versión que espera la base |
| — | [../herramientas/copiar_zona_horaria.ps1](../herramientas/copiar_zona_horaria.ps1) | PowerShell | Copia `timezlrg_45.dat` desde la imagen slim (ver hallazgo 2) |
| 2 | [S13_02_reiniciar.sql](S13_02_reiniciar.sql) | `-Sysdba` | Reinicia y comprueba que las zonas horarias cargaron |
| 3 | [S13_03_archivelog.sql](S13_03_archivelog.sql) | `-Sysdba` | FRA dentro del volumen y modo ARCHIVELOG |
| 4 | [S13_04_rman_nivel0.rman](S13_04_rman_nivel0.rman) | RMAN | Configuración y nivel 0 `PLUS ARCHIVELOG` |
| 5 | [S13_05_cambio_legitimo.sql](S13_05_cambio_legitimo.sql) | dueño | Cambio 1 (válido): +5 % al costo del producto 1 |
| 6 | [S13_06_rman_nivel1.rman](S13_06_rman_nivel1.rman) | RMAN | Incremental de nivel 1 |
| 7 | [S13_07_incidente.sql](S13_07_incidente.sql) | dueño | Hora segura y cambio 2 (el incidente): `UPDATE` sin `WHERE` |
| 8 | [S13_08_pitr.rman](S13_08_pitr.rman) | RMAN | `SET UNTIL TIME`, `RESTORE`, `RECOVER`, `OPEN RESETLOGS` |
| 9 | [S13_09_verificar.sql](S13_09_verificar.sql) | dueño | Qué cambio sobrevivió |
| 10 | [S13_10_rman_post_resetlogs.rman](S13_10_rman_post_resetlogs.rman) | RMAN | Nivel 0 nuevo para la encarnación nueva |
| 11 | [S13_11_directorio_dp.sql](S13_11_directorio_dp.sql) | `-Dba` | Directorio de Data Pump dentro del volumen |

Los `.rman` se ejecutan dentro del contenedor:

```powershell
docker cp bd2\S13\S13_04_rman_nivel0.rman sge-jd-bd2:/tmp/n0.rman
docker exec sge-jd-bd2 bash -lc "rman target / cmdfile=/tmp/n0.rman"
```

El export (paso 12) se corrió así:

```powershell
docker exec -e ORACLE_PDB_SID=FREEPDB1 sge-jd-bd2 bash -lc 'expdp \"/ as sysdba\" schemas=JDREFRIGERACION directory=DP_SGEJD dumpfile=sgejd_s13.dmp logfile=sgejd_s13_exp.log reuse_dumpfiles=yes'
```

Las salidas completas quedaron en `bd2/salidas/S13_*.txt`, que no se sube al repositorio.

## 1. Antes de empezar: dos hallazgos de la imagen

**Hallazgo 1: `23-slim` no trae RMAN.**
- De S1 a S12 se usó `gvenzl/oracle-free:23-slim`, la misma imagen que el 4200 y el 4300.
- Esa variante quita RMAN para pesar menos. Para esta sesión, `compose-bd2.yml` pasó a `gvenzl/oracle-free:23` (la completa, ~1,1 GB más).
- El volumen con los datos es el mismo. Solo cambió el software.
- **Consecuencia para el proyecto:** hoy desarrollo y producción **no pueden** hacer backups RMAN. Su único respaldo es Data Pump (`scripts/respaldar_sgejd.ps1`).

**Hallazgo 2: la imagen completa trae una versión de zonas horarias más vieja que la base.**
- Al encender con la imagen nueva, `S13_01` mostró:

  | Dato | Valor |
  |---|---|
  | `V$TIMEZONE_FILE` | sin filas |
  | `V$TIMEZONE_NAMES` | 0 zonas |
  | `DST_PRIMARY_TT_VERSION` | 45 |
  | `FREEPDB1` | `altered with errors or warnings` |

- La base se creó con la slim, que trae `timezlrg_45.dat`. La completa solo trae `timezlrg_43.dat`, más viejo, y Oracle no baja de versión.
- No se ve en el uso normal, porque el proyecto no usa `TIMESTAMP WITH TIME ZONE` con regiones. Una consulta que sí las use fallaría con **ORA-01804**.
- **Solución:** copiar `timezlrg_45.dat` desde la imagen slim al `ORACLE_HOME` y reiniciar (`copiar_zona_horaria.ps1`, `S13_02`). Resultado: `timezlrg_45.dat`, versión 45, 2475 zonas en la raíz y en FREEPDB1, sin violaciones pendientes.
- **El archivo queda en la capa del contenedor, no en el volumen.** Si se recrea el contenedor, hay que volver a correr el script.
- Este hallazgo refuerza la tarea de S7 de **fijar la imagen por digest**: cambiar de imagen sin querer cambia el software bajo los mismos datos.

## 2. ARCHIVELOG con la FRA dentro del volumen

| Dato | Antes | Después |
|---|---|---|
| Modo | `No Archive Mode` | `Archive Mode`, archivado automático |
| `db_recovery_file_dest` | vacío | `/opt/oracle/oradata/FRA` (**dentro** del volumen) |
| `db_recovery_file_dest_size` | 0 | 10 GB (el disco del volumen tiene 918 GB libres) |
| Primer archivelog | — | `/opt/oracle/oradata/FRA/FREE/archivelog/2026_11_10/o1_mf_1_21_….arc` |

- Esto cierra la tarea pendiente de S9: en el contenedor, todo lo que esté fuera de `/opt/oracle/oradata` se pierde al recrearlo.
- ARCHIVELOG exige `SHUTDOWN`, `STARTUP MOUNT`, `ALTER DATABASE ARCHIVELOG` y `OPEN`. Es la única parada de la sesión que haría falta en producción.
- Se agregó `ALTER PLUGGABLE DATABASE ALL SAVE STATE` para que FREEPDB1 abra sola después de cada reinicio.

Configuración RMAN aplicada:
- `CONTROLFILE AUTOBACKUP ON`: tras cada backup, RMAN guarda solo el controlfile y el spfile, que son el mapa para poder restaurar.
- `RETENTION POLICY TO RECOVERY WINDOW OF 7 DAYS`.
- `BACKUP OPTIMIZATION ON`.

## 3. Nivel 0 frente a nivel 1

| | Nivel 0 | Nivel 1 (diferencial) |
|---|---|---|
| Tiempo | **28 s** | **12 s** |
| Tamaño en disco | ~2,4 GB (1,1 GB raíz + 899 MB FREEPDB1 + 466 MB PDB$SEED) | **~720 KB** de datafiles (400 KB + 320 KB) más los archivelogs nuevos |
| Bloques de `TS_SGEJD_VENTAS` (archivo 25, 115 200 bloques) | 37 078 (32 %) | **1** |
| Bloques de `SYSTEM` (archivo 1, 119 128 bloques) | 100 010 (84 %) | 7 |
| Datafiles saltados | — | 2, 4 y 20 (`skipping datafile …; already backed up 1 time(s)`): los de PDB$SEED, que es de solo lectura |
| Archivelogs saltados | — | secuencias 21 a 23 (`already backed up`), por `BACKUP OPTIMIZATION ON` |

Lecturas:
- **Incluso el nivel 0 copia menos que el archivo.** RMAN se salta los bloques nunca usados: de 4,5 GB de datafiles copió ~2,4 GB. `TS_SGEJD_VENTAS` mide 900 MB, pero solo el 32 % tiene datos (el tablespace creció en S9 y S11).
- **El nivel 1 es lo que hace viable un backup diario.** Una actualización de un producto cambió unos pocos bloques, y el nivel 1 copió justo eso. En el uso real (unas 30 ventas al día) el nivel 1 diario sería de pocos MB.
- **Hallazgo de sintaxis:** con `BACKUP … PLUS ARCHIVELOG TAG 'S13_NIVEL0'`, el TAG solo quedó en los dos backups de archivelogs (claves 1 y 5). El backup de datafiles recibió una etiqueta automática (`TAG20261110T210628`). Para etiquetar todo hay que separar los comandos o repetir el TAG en cada parte.

## 4. El incidente y la recuperación a un punto en el tiempo (PITR)

| Hora (Lima) | Hecho | Estado de `PRODUCTOS` |
|---|---|---|
| 21:06:28 | Nivel 0 | producto 1: costo 330, margen 28; suma de márgenes 1857 |
| 21:07:25 | **Cambio 1, legítimo:** costo del producto 1 +5 % | producto 1: costo **346,5** |
| 21:07:37 | Nivel 1 | |
| 21:07:58 | Hora segura anotada | |
| 21:08:03 | **Cambio 2, el incidente:** `UPDATE productos SET margen_porcentaje = 0` sin `WHERE`, con `COMMIT` | 56 filas; suma de márgenes **0**. Toda proforma nueva saldría al costo, sin ganancia |
| 21:08:04 | Se detecta y se decide recuperar hasta las **21:08:00** | |

`S13_08_pitr.rman` hizo:
- `SHUTDOWN`, `STARTUP MOUNT` y `SET UNTIL TIME '2026-11-10 21:08:00'`.
- `RESTORE DATABASE`: RMAN eligió solo el nivel 0 para restaurar.
- `RECOVER DATABASE`: aplicó los dos backups de nivel 1 (`starting incremental datafile backup set restore`) y luego el redo hasta la hora pedida (`media recovery complete`).
- `ALTER DATABASE OPEN RESETLOGS`.

Verificación (`S13_09`):

| Dato | Después del incidente | Después del PITR |
|---|---|---|
| Producto 1: costo | 346,5 | **346,5**: el cambio legítimo **sobrevivió** |
| Suma de márgenes (56 productos) | 0 | **1857**: el incidente **desapareció** |
| Ventas | 27 200 | 27 200 |

`LIST INCARNATION` muestra la encarnación 3 como `CURRENT`, creada por el `RESETLOGS` en el SCN 3 326 533. Desde ahí los backups de la encarnación 2 ya no sirven para avanzar. Por eso `S13_10` tomó un nivel 0 nuevo (22,5 s). `DELETE OBSOLETE` no borró nada: con una ventana de 7 días, todo lo de hoy sigue dentro de la ventana.

### RTO y RPO medidos

| | Valor medido | Qué significa aquí | En producción real |
|---|---|---|---|
| **RTO** (tiempo hasta volver a operar) | **33 s** (`real 0m33.264s`, desde el `SHUTDOWN` hasta el `OPEN` de los PDB) | Restaurar ~2,4 GB y aplicar 1 nivel 1 + redo | Hay que sumar lo que tarda en **notarse** el error y en decidir. En JD eso puede ser horas: el margen en 0 recién se ve al revisar una proforma |
| **RPO** (datos perdidos) | **0 datos legítimos**; se descartaron los 4 s entre las 21:08:00 y el incidente | Se volvió al segundo exacto anterior al error, gracias al redo archivado | El PITR de toda la base también **descarta todo lo bueno posterior al incidente** (ventas, cobros…). Si el error se nota a las 4 horas, se pierden 4 horas de trabajo de toda la empresa |

La última fila es la lección de la sesión: un PITR de toda la base es un martillo. Opciones más finas, para un error de una sola tabla, que **no se probaron aquí**:
- `FLASHBACK TABLE … TO TIMESTAMP`, o una consulta `AS OF TIMESTAMP` para copiar las filas buenas. Dependen del undo, que se recicla rápido.
- Un PITR solo de FREEPDB1 (`RECOVER PLUGGABLE DATABASE`). FREEPDB1 tiene undo local (archivo 23), así que es posible.
- Restaurar el export de Data Pump en otro esquema y copiar las filas.

## 5. Data Pump: el respaldo lógico

| Dato | Valor |
|---|---|
| Comando | `expdp` del esquema `JDREFRIGERACION`, conectado con `ORACLE_PDB_SID=FREEPDB1` (igual que `scripts/respaldar_sgejd.ps1`) |
| Directorio | `DP_SGEJD` → `/opt/oracle/oradata/dpdump`, **dentro** del volumen |
| Directorio por defecto | `DATA_PUMP_DIR` → `/opt/oracle/admin/FREE/dpdump/5310…`, **fuera** del volumen |
| Resultado | `successfully completed`, 49 s (55 s con el arranque de Docker); `sgejd_s13.dmp` de **27 MB**, 96 tablas o particiones exportadas, sin errores |
| `PRODUCTOS` | 56 filas, ya con el margen corregido |

- El script del proyecto usa `DATA_PUMP_DIR`, que está fuera del volumen. No es un problema, porque copia el `.dmp` a `C:\SGE-JD\respaldos` y lo borra del contenedor.
- Aquí se usó un directorio dentro del volumen porque el dump se queda en la base.

| | RMAN | Data Pump |
|---|---|---|
| Copia | Bloques físicos de toda la base | Filas y definiciones de un esquema |
| Tamaño hoy | ~2,4 GB (nivel 0); KB a MB (nivel 1) | 27 MB |
| Recupera a | **Cualquier segundo** (con ARCHIVELOG) | Solo al momento del export |
| Sirve para | Disco dañado, base corrupta, error humano reciente | Mover datos entre bases o versiones, restaurar una tabla, copia legible fuera de Oracle |
| Requiere | Imagen completa, ARCHIVELOG, espacio en la FRA | Nada extra (ya funciona en el 4200 y el 4300) |

## 6. Estrategia propuesta para JD Refrigeración

| Qué | Cuándo | Dónde | Retención | Cubre |
|---|---|---|---|---|
| ARCHIVELOG + FRA | Siempre | `/opt/oracle/oradata/FRA` (volumen) | 7 días (`RECOVERY WINDOW`) | Errores humanos con RPO de segundos |
| RMAN nivel 0 | Domingo de madrugada | FRA | 2 semanas | Base de toda recuperación |
| RMAN nivel 1 | Lunes a sábado de madrugada | FRA | Hasta el siguiente nivel 0 | Acorta el `RECOVER` |
| Data Pump del esquema | Diario (`respaldo_automatico.ps1`: script listo, la tarea todavía no está programada) | `C:\SGE-JD\respaldos` **y una carpeta fuera de la PC** | Últimas 14 copias (lo que ya hace el script) | Se pierde el disco o la PC entera |
| Prueba de restauración | Mensual | Base de práctica | — | Saber que los respaldos **sirven** |

- **El punto débil:** la FRA está en el mismo disco que la base. Si muere el disco, se pierden la base, los archivelogs y los backups juntos. Contra eso solo protege la copia fuera de la PC, y esa carpeta todavía no está decidida (pendiente con Lisandro).
- Mientras tanto, el RPO real ante la pérdida del equipo es **el tiempo desde la última copia sacada de la PC**.

## 7. Reflexión: GitLab, enero de 2017

- En 2017 un ingeniero de GitLab borró por error el directorio de datos de la base PostgreSQL **principal**, creyendo que estaba en la réplica.
- Al buscar el respaldo, varios mecanismos que creían tener **no funcionaban**:
  - el volcado periódico fallaba en silencio;
  - los avisos de esa falla no les llegaban;
  - la replicación ya estaba rota;
  - las instantáneas de disco no estaban activas para la base.
- Recuperaron desde una copia de unas 6 horas antes que existía casi por casualidad. Perdieron esas horas de datos de usuarios.
- Lo contaron en público y en vivo, y por eso el caso se enseña.

**¿El incremental de esta sesión de verdad capturó el cambio correcto?** Sí, y se puede comprobar en tres puntos:
1. `PRODUCTOS` vive en el tablespace `USERS` de FREEPDB1 (archivo 24). El nivel 1 copió solo **2 bloques** de ese archivo de 3968, en el único momento en que lo único que había cambiado en `USERS` era el producto 1.
2. En el PITR, RMAN leyó esa pieza de nivel 1 (`…_oh7mhb0f_.bkp`).
3. El costo **346,5** volvió.

Tener varios mecanismos no garantiza nada si ninguno se prueba. GitLab tenía cinco y descubrió que no funcionaban
recién cuando los necesitó.

Lo que se lleva este proyecto:
1. **Un respaldo que nunca se restauró no es un respaldo.** Esta sesión restauró de verdad y midió 33 s. Por eso la tabla de estrategia incluye una prueba mensual en la base de práctica.
2. **Las fallas silenciosas son las peores.**
   - `respaldo_automatico.ps1` ya anota todo en `respaldos\respaldo_automatico.log`, pero un registro que nadie lee también es silencioso. Hay que revisarlo, o que avise.
   - Aquí mismo hubo dos fallas silenciosas: la zona horaria (FREEPDB1 abrió "con advertencias" y nada más avisaba) y el TAG que no se aplicó a los datafiles.
3. **Confirmar en qué máquina se está.** El error de GitLab fue de contexto. En esta PC conviven el 4200 (`SGE-JD`), el 4300 (`sge-jd-oracle-prod`) y la práctica (`sge-jd-bd2`). Por eso cada script de BD2 nombra el contenedor y el puerto explícitamente, y `restaurar_practica.ps1` solo apunta a `sge-jd-bd2`.
4. **El respaldo debe vivir lejos del dato.** La FRA en el mismo disco protege del error humano, no de perder el equipo.

## 8. Qué pasa a la integración con LP2

| Tema | Propuesta | Estado |
|---|---|---|
| Imagen | Usar `gvenzl/oracle-free:23` (completa) en `compose-prod.yml` para tener RMAN, fijada por digest (S7), y copiar `timezlrg_45.dat` o crear la base con la misma imagen | Propuesto |
| ARCHIVELOG en producción | FRA en `/opt/oracle/oradata/FRA`, 10 GB, ventana de 7 días; una sola parada para activarlo | Probado en práctica |
| RMAN programado | Nivel 0 semanal y nivel 1 diario, junto con el Data Pump que ya existe | Propuesto |
| Copia fuera de la PC | Depende de la carpeta que elija Lisandro | Pendiente |
