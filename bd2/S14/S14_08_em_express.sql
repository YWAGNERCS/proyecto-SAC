-- BD2 · S14 · Paso 8: ¿hay Enterprise Manager Express en esta instancia? Como SYSDBA.
set echo on feedback on linesize 200 serveroutput on
SELECT banner_full FROM v$version;
SELECT DBMS_XDB_CONFIG.GETHTTPSPORT AS puerto_https, DBMS_XDB_CONFIG.GETHTTPPORT AS puerto_http FROM dual;
EXEC DBMS_XDB_CONFIG.SETHTTPSPORT(5500)
SELECT DBMS_XDB_CONFIG.GETHTTPSPORT AS puerto_https FROM dual;
SELECT comp_id, status FROM dba_registry WHERE comp_id = 'XDB';
exit
