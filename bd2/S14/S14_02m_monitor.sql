-- BD2 · S14 · Sesión C (monitoreo): quién espera a quién, mientras A y B siguen abiertas. Como SYSTEM (-Dba).
set echo off feedback on linesize 220 pagesize 100
column username format a10
column module format a24
column action format a24
column event format a32
column sql_text format a70
column lock_type format a12
column mode_held format a10
column mode_requested format a10
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS') AS hora_monitor FROM dual;
PROMPT ===== V$SESSION: las sesiones del backend =====
SELECT s.sid, s.serial#, s.username, s.status, s.module, s.action, s.event, s.blocking_session, s.seconds_in_wait
FROM v$session s WHERE s.username = 'JD_APP' ORDER BY s.sid;
PROMPT ===== DBA_BLOCKERS =====
SELECT * FROM dba_blockers;
PROMPT ===== DBA_WAITERS =====
SELECT waiting_session, holding_session, lock_type, mode_held, mode_requested FROM dba_waiters;
PROMPT ===== Qué intenta hacer la que espera, y sobre qué fila =====
SELECT s.sid, o.object_name AS tabla, s.row_wait_obj#, s.row_wait_row#, q.sql_text
FROM v$session s JOIN v$sql q ON q.sql_id = s.sql_id AND q.child_number = s.sql_child_number
LEFT JOIN dba_objects o ON o.object_id = s.row_wait_obj#
WHERE s.blocking_session IS NOT NULL;
PROMPT ===== V$LOCK: el candado de transacción (TX) =====
SELECT sid, type, lmode, request, block FROM v$lock WHERE type = 'TX' ORDER BY sid;
exit
