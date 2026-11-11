-- BD2 · S13 · Paso 3: área de recuperación (FRA) DENTRO del volumen y modo ARCHIVELOG. Como SYSDBA.
-- Hallazgo S9: el db_recovery_file_dest por defecto queda fuera de /opt/oracle/oradata, o sea fuera del volumen:
-- si se recrea el contenedor, se pierden los archivelogs y los backups. Por eso va en /opt/oracle/oradata/FRA.
set echo on feedback on linesize 200
column name format a40
column value format a40
ARCHIVE LOG LIST
SELECT name, value FROM v$parameter WHERE name IN ('db_recovery_file_dest', 'db_recovery_file_dest_size');
ALTER SYSTEM SET db_recovery_file_dest_size = 10G SCOPE = BOTH;
ALTER SYSTEM SET db_recovery_file_dest = '/opt/oracle/oradata/FRA' SCOPE = BOTH;
SHUTDOWN IMMEDIATE
STARTUP MOUNT
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;
ALTER PLUGGABLE DATABASE ALL OPEN;
ALTER PLUGGABLE DATABASE ALL SAVE STATE;
ARCHIVE LOG LIST
SELECT log_mode FROM v$database;
ALTER SYSTEM ARCHIVE LOG CURRENT;
SELECT COUNT(*) AS archivelogs, MAX(name) AS ultimo FROM v$archived_log WHERE name IS NOT NULL;
SELECT name, open_mode FROM v$pdbs;
exit
