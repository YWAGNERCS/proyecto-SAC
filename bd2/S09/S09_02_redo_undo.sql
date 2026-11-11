-- ============================================================
-- BD2 · S9 · Paso 2: redo, undo y modo de archivado. Y el cambio de redo que propuso S7.
-- Como SYSDBA (los redo logs son de la instancia, no de la PDB):
--   .\bd2\herramientas\sql.ps1 bd2\S09\S09_02_redo_undo.sql -Sysdba
-- ============================================================
set echo off feedback on linesize 200 pagesize 100
column member format a50
column tablespace_name format a12

PROMPT ===== Antes: redo logs (V$LOG, V$LOGFILE) =====
SELECT group#, sequence#, ROUND(bytes / 1024 / 1024) AS mb, members, status, archived FROM v$log ORDER BY group#;
SELECT group#, member FROM v$logfile ORDER BY group#;

PROMPT ===== Undo (de la PDB de SGE-JD) =====
SELECT c.name AS contenedor, t.tablespace_name, t.contents, t.retention
FROM cdb_tablespaces t JOIN v$containers c ON c.con_id = t.con_id WHERE t.contents = 'UNDO' ORDER BY c.name;
SELECT name, value FROM v$parameter WHERE name IN ('undo_management', 'undo_retention', 'undo_tablespace');

PROMPT ===== Modo de archivado =====
SELECT log_mode, force_logging FROM v$database;
ARCHIVE LOG LIST

PROMPT ===== Cambio propuesto en S7: de 2 x 10 MB a 3 x 200 MB =====
-- Primero se agregan los grupos nuevos (dentro del volumen), después se retiran los viejos cuando quedan INACTIVE
ALTER DATABASE ADD LOGFILE GROUP 3 ('/opt/oracle/oradata/FREE/redo03.log') SIZE 200M;
ALTER DATABASE ADD LOGFILE GROUP 4 ('/opt/oracle/oradata/FREE/redo04.log') SIZE 200M;
ALTER DATABASE ADD LOGFILE GROUP 5 ('/opt/oracle/oradata/FREE/redo05.log') SIZE 200M;
-- Mover el log actual a un grupo nuevo y forzar el checkpoint, para que los grupos 1 y 2 queden INACTIVE
ALTER SYSTEM SWITCH LOGFILE;
ALTER SYSTEM SWITCH LOGFILE;
ALTER SYSTEM CHECKPOINT;
SELECT group#, ROUND(bytes / 1024 / 1024) AS mb, status FROM v$log ORDER BY group#;
ALTER DATABASE DROP LOGFILE GROUP 1;
ALTER DATABASE DROP LOGFILE GROUP 2;

PROMPT ===== Después =====
SELECT group#, sequence#, ROUND(bytes / 1024 / 1024) AS mb, members, status FROM v$log ORDER BY group#;
-- (Los archivos redo01.log y redo02.log quedan en disco: Oracle no los borra; se eliminan a mano.)
exit
