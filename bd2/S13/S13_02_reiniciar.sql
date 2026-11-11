-- BD2 · S13 · Paso 2: reiniciar la instancia para que lea el archivo de zonas horarias copiado. Como SYSDBA.
set echo on feedback on
SHUTDOWN IMMEDIATE
STARTUP
SELECT * FROM v$timezone_file;
SELECT COUNT(*) AS zonas_horarias FROM v$timezone_names;
ALTER SESSION SET CONTAINER = FREEPDB1;
SELECT COUNT(*) AS zonas_horarias_en_pdb FROM v$timezone_names;
SELECT type, status, SUBSTR(message, 1, 120) AS mensaje FROM pdb_plug_in_violations WHERE status <> 'RESOLVED';
exit
