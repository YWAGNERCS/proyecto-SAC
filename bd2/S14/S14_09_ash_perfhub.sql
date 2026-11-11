-- BD2 · S14 · Paso 9: lo que EM Express mostraba, sin EM Express (desoportado desde 23ai).
-- (a) ASH: una muestra por segundo de cada sesión activa; ahí quedaron los dos bloqueos.
-- (b) DBMS_PERF.REPORT_PERFHUB: el mismo "Performance Hub" de EM Express, como un HTML que se abre en el navegador.
-- ASH y Performance Hub son del Diagnostics Pack: en la edición Free se pueden usar; en EE se licencian aparte (S10).
-- Como SYSDBA. ASH se consulta dentro de FREEPDB1: JD_APP es usuario local del PDB y no existe en DBA_USERS de la raíz.
set echo off feedback on linesize 220 pagesize 100
column module format a26
column event format a32
column hora format a8
ALTER SESSION SET CONTAINER = FREEPDB1;
PROMPT ===== ASH: segundos muestreados por sesión y evento (últimos 20 minutos, sesiones de JD_APP) =====
SELECT h.session_id AS sid, h.module, NVL(h.event, 'ON CPU') AS event, h.blocking_session AS bloqueada_por,
       COUNT(*) AS segundos, TO_CHAR(MIN(h.sample_time), 'HH24:MI:SS') AS desde, TO_CHAR(MAX(h.sample_time), 'HH24:MI:SS') AS hasta
FROM v$active_session_history h JOIN dba_users u ON u.user_id = h.user_id
WHERE u.username = 'JD_APP' AND h.sample_time > SYSTIMESTAMP - INTERVAL '20' MINUTE
GROUP BY h.session_id, h.module, NVL(h.event, 'ON CPU'), h.blocking_session ORDER BY desde;
set long 20000000 longchunksize 200000 pagesize 0 linesize 32767 trimspool on heading off feedback off termout off
spool /tmp/perfhub_s14.html
SELECT DBMS_PERF.REPORT_PERFHUB(is_realtime => 1, outer_start_time => SYSDATE - 20/1440, outer_end_time => SYSDATE,
       selected_start_time => SYSDATE - 20/1440, selected_end_time => SYSDATE, type => 'ACTIVE') FROM dual;
spool off
exit
