-- ============================================================
-- BD2 · S7 · Arquitectura Oracle e instancia · SGE-JD
-- Paso 1: memoria, procesos, nombre de la instancia, SYSDBA y estrategia de memoria.
-- Como SYSDBA, por autenticación del sistema operativo dentro del contenedor (sin clave):
--   .\bd2\herramientas\sql.ps1 bd2\S07\S07_01_instancia.sql -Sysdba
-- ============================================================
set echo off feedback off linesize 200 pagesize 200
column name format a40
column value format a70
column description format a60

PROMPT ===== Quién soy y dónde estoy =====
SHOW USER
SHOW CON_NAME
SELECT con_id, name, open_mode FROM v$pdbs ORDER BY con_id;

PROMPT
PROMPT ===== SGA: resumen (V$SGA) =====
SELECT name, ROUND(value / 1024 / 1024, 1) AS mb FROM v$sga;
PROMPT ===== SGA por componente (V$SGAINFO) =====
SELECT name, ROUND(bytes / 1024 / 1024, 1) AS mb, resizeable FROM v$sgainfo ORDER BY bytes DESC;

PROMPT
PROMPT ===== PGA (V$PGASTAT) =====
SELECT name, ROUND(value / 1024 / 1024, 1) AS mb FROM v$pgastat
WHERE name IN ('aggregate PGA target parameter', 'total PGA allocated', 'total PGA inuse',
               'maximum PGA allocated', 'total PGA used for auto workareas');

PROMPT
PROMPT ===== Procesos background en ejecución (V$BGPROCESS, PADDR <> '00') =====
SELECT name, description FROM v$bgprocess WHERE paddr <> '00' ORDER BY name;
SELECT COUNT(*) AS procesos_background_activos FROM v$bgprocess WHERE paddr <> '00';

PROMPT
PROMPT ===== La instancia (V$INSTANCE) y la base (V$DATABASE) =====
column host_name format a20
column version_full format a12
SELECT instance_name, host_name, version_full, status, TO_CHAR(startup_time, 'DD/MM/YYYY HH24:MI') AS arranque,
       database_type FROM v$instance;
SELECT name, cdb, log_mode, open_mode, platform_name FROM v$database;

PROMPT
PROMPT ===== Quién puede conectarse AS SYSDBA (archivo de contraseñas) =====
SELECT username, sysdba, sysoper, sysbackup, account_status FROM v$pwfile_users;

PROMPT
PROMPT ===== Estrategia de memoria (V$PARAMETER) =====
SELECT name, value FROM v$parameter
WHERE name IN ('spfile', 'sga_target', 'sga_max_size', 'memory_target', 'memory_max_target',
               'pga_aggregate_target', 'pga_aggregate_limit', 'cpu_count', 'processes', 'sessions')
ORDER BY name;

PROMPT
PROMPT ===== Límites de Oracle Free (lo que esta edición deja usar) =====
SELECT name, value FROM v$parameter WHERE name IN ('cpu_count', 'sga_max_size') ;
SELECT ROUND(SUM(bytes) / 1024 / 1024 / 1024, 2) AS gb_datos_usuario
FROM cdb_data_files WHERE con_id = (SELECT con_id FROM v$pdbs WHERE name = 'FREEPDB1');

PROMPT
PROMPT ===== Dónde está el alert log (V$DIAG_INFO) =====
SELECT name, value FROM v$diag_info WHERE name IN ('ADR Home', 'Diag Trace', 'Diag Alert');

PROMPT
PROMPT ===== Sesiones conectadas ahora, por usuario y programa =====
column username format a18
column program format a40
SELECT username, program, COUNT(*) AS sesiones FROM v$session
WHERE type = 'USER' GROUP BY username, program ORDER BY username;
exit
