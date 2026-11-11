-- BD2 · S14 · Paso 1: sesiones conectadas a FREEPDB1, ahora mismo. Como SYSTEM (-Dba).
-- V$SESSION vive en la SGA: es una foto del momento y se pierde al reiniciar.
set echo off feedback on linesize 220 pagesize 100
column username format a16
column osuser format a10
column machine format a14
column program format a28
column module format a24
column event format a34
SELECT sid, serial#, username, status, type, osuser, machine, program, module, event, sql_id
FROM v$session WHERE username IS NOT NULL ORDER BY sid;
PROMPT ===== Resumen: usuarios frente a procesos de la instancia =====
SELECT type, NVL(username, '(proceso interno)') AS quien, status, COUNT(*) AS sesiones
FROM v$session GROUP BY type, NVL(username, '(proceso interno)'), status ORDER BY 1, 2;
exit
