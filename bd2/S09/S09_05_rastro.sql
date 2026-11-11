-- ============================================================
-- BD2 · S9 · Paso 5: lo que quedó en UNIFIED_AUDIT_TRAIL. Como SYSTEM en FREEPDB1:
--   .\bd2\herramientas\sql.ps1 bd2\S09\S09_05_rastro.sql -Dba
-- ============================================================
set echo off feedback on linesize 220 pagesize 100
column dbusername format a12
column action_name format a14
column objeto format a28
column politica format a22
column hora format a8
column programa format a26
SELECT TO_CHAR(event_timestamp, 'HH24:MI:SS') AS hora, dbusername, action_name,
       object_schema || '.' || object_name AS objeto, return_code,
       SUBSTR(unified_audit_policies, 1, 22) AS politica, SUBSTR(client_program_name, 1, 26) AS programa
FROM unified_audit_trail
WHERE event_timestamp > SYSTIMESTAMP - INTERVAL '30' MINUTE
  AND (dbusername IN ('JD_APP', 'JD_REPORTES') OR action_name = 'LOGON' AND return_code <> 0)
ORDER BY event_timestamp;
exit
