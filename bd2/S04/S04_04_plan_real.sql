-- ============================================================
-- BD2 · S4 · Paso 4: el plan que Oracle usó DE VERDAD en S04_03, con filas estimadas (E-Rows) frente a
-- filas reales (A-Rows) y los valores de las variables que miró al optimizar (Peeked Binds).
-- Como SYSTEM (leer V$SQL necesita privilegios de diccionario; eso se ordena en S7 y S8):
--   .\bd2\herramientas\sql.ps1 bd2\S04\S04_04_plan_real.sql -Dba
-- ============================================================
set echo off feedback off linesize 220 pagesize 300
column sql_id new_value v_sql_hib
SELECT sql_id FROM v$sql
WHERE sql_text LIKE '%S4_HIBERNATE%' AND sql_text NOT LIKE '%v$sql%' AND parsing_schema_name = 'JDREFRIGERACION' AND command_type = 3
ORDER BY last_active_time DESC FETCH FIRST 1 ROW ONLY;
PROMPT ================= Plan real: consulta de Hibernate =================
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR('&v_sql_hib', NULL, 'ALLSTATS LAST +PEEKED_BINDS -BYTES'));

column sql_id new_value v_sql_ree
SELECT sql_id FROM v$sql
WHERE sql_text LIKE '%S4_REESCRITA%' AND sql_text NOT LIKE '%v$sql%' AND parsing_schema_name = 'JDREFRIGERACION' AND command_type = 3
ORDER BY last_active_time DESC FETCH FIRST 1 ROW ONLY;
PROMPT ================= Plan real: consulta reescrita =================
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR('&v_sql_ree', NULL, 'ALLSTATS LAST +PEEKED_BINDS -BYTES'));

PROMPT ================= Promedio por ejecución según V$SQL =================
column texto format a14
SELECT CASE WHEN sql_text LIKE '%S4_HIBERNATE%' THEN 'Hibernate' ELSE 'Reescrita' END AS texto,
       executions, ROUND(elapsed_time / NULLIF(executions, 0) / 1000, 2) AS ms_por_ejecucion,
       ROUND(buffer_gets / NULLIF(executions, 0)) AS lecturas_logicas
FROM v$sql
WHERE (sql_text LIKE '%S4_HIBERNATE%' OR sql_text LIKE '%S4_REESCRITA%')
  AND sql_text NOT LIKE '%v$sql%' AND parsing_schema_name = 'JDREFRIGERACION' AND command_type = 3;
exit
