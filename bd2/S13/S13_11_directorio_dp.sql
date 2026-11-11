-- BD2 · S13 · Paso 11: directorio de Data Pump DENTRO del volumen. Como SYSTEM en FREEPDB1 (-Dba).
-- DATA_PUMP_DIR apunta a /opt/oracle/admin/FREE/dpdump/..., fuera del volumen: un dump ahí se pierde al recrear el contenedor.
set echo off feedback on linesize 200
column directory_path format a70
SELECT directory_name, directory_path FROM dba_directories WHERE directory_name = 'DATA_PUMP_DIR';
CREATE OR REPLACE DIRECTORY dp_sgejd AS '/opt/oracle/oradata/dpdump';
SELECT directory_name, directory_path FROM dba_directories WHERE directory_name = 'DP_SGEJD';
exit
