-- ============================================================
-- BD2 · S10 · Paso 3: intento real de AWR, y el mismo diagnóstico con vistas V$ (sin licencia).
-- Como SYSDBA:   .\bd2\herramientas\sql.ps1 bd2\S10\S10_03_awr_y_vsql.sql -Sysdba
-- Antes, la consulta de Rentabilidad tiene que haberse EJECUTADO (S10_03b, como JDREFRIGERACION):
-- DISPLAY_CURSOR y V$SQL solo ven lo que pasó por la memoria compartida.
-- ============================================================
set echo off feedback on linesize 220 pagesize 200 serveroutput on
whenever sqlerror continue
column name format a36
column value format a20

PROMPT ===== 1) ¿Qué permite la licencia según la propia instancia? =====
SELECT name, value FROM v$parameter WHERE name IN ('control_management_pack_access', 'statistics_level');

PROMPT ===== 2) Snapshots que ya existían (automáticos) =====
ALTER SESSION SET CONTAINER = FREEPDB1;
SELECT COUNT(*) AS snapshots_previos, MIN(begin_interval_time) AS el_mas_antiguo FROM dba_hist_snapshot;

PROMPT ===== 3) Intento real de crear un snapshot de AWR =====
BEGIN
    DBMS_WORKLOAD_REPOSITORY.CREATE_SNAPSHOT();
    DBMS_OUTPUT.PUT_LINE('CREATE_SNAPSHOT terminó sin error');
END;
/
SELECT snap_id, TO_CHAR(begin_interval_time, 'DD/MM HH24:MI:SS') AS inicio, TO_CHAR(end_interval_time, 'DD/MM HH24:MI:SS') AS fin
FROM dba_hist_snapshot ORDER BY snap_id DESC FETCH FIRST 3 ROWS ONLY;

PROMPT ===== 4) El mismo diagnóstico sin AWR: las consultas más caras ahora mismo (V$SQL) =====
column texto format a70
SELECT sql_id, executions AS ejec, ROUND(elapsed_time / 1000) AS ms_total,
       ROUND(elapsed_time / NULLIF(executions, 0) / 1000, 2) AS ms_por_ejec,
       ROUND(buffer_gets / NULLIF(executions, 0)) AS lecturas_por_ejec,
       SUBSTR(REPLACE(sql_text, CHR(10), ' '), 1, 70) AS texto
FROM v$sql
WHERE parsing_schema_name = 'JDREFRIGERACION' AND command_type = 3
ORDER BY elapsed_time DESC FETCH FIRST 5 ROWS ONLY;

PROMPT ===== 5) Plan REAL de la consulta de Rentabilidad (V$SQL_PLAN vía DISPLAY_CURSOR) =====
column sql_id new_value v_sql
SELECT sql_id FROM v$sql WHERE sql_text LIKE '%S10_RENTABILIDAD%' AND sql_text NOT LIKE '%v$sql%' AND command_type = 3
ORDER BY last_active_time DESC FETCH FIRST 1 ROW ONLY;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR('&v_sql', NULL, 'ALLSTATS LAST -BYTES'));
exit
