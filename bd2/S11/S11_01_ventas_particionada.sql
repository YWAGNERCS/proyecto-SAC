-- ============================================================
-- BD2 · S11 · Particionamiento y escalabilidad · SGE-JD
-- Paso 1: VENTAS_PART, copia de VENTAS particionada por RANGO MENSUAL de FECHA_VENTA (INTERVAL), y pruning.
-- Como JDREFRIGERACION:   .\bd2\herramientas\sql.ps1 bd2\S11\S11_01_ventas_particionada.sql
--
-- No se particiona VENTAS en sí (cambiaría la estructura que usa el backend): se construye una copia con los
-- datos reales de la práctica (las ~27 000 ventas en orden de fecha de S10), como hicieron S4 y S5.
-- Patrón de acceso real de VENTAS (S4, S5, S10): casi todas las consultas filtran por un periodo (un día, un mes,
-- un año) y nunca se borra una venta (SUNAT exige conservarlas). Por eso RANGE por fecha, y por eso aquí NO se
-- mide DROP PARTITION sobre ventas (eso va en S11_03, sobre la auditoría).
-- ============================================================
set echo off feedback on linesize 200 pagesize 200 timing on
whenever sqlerror continue
column partition_name format a14
column high_value format a40
DROP TABLE ventas_part PURGE;

PROMPT ===== 1) Tabla particionada: un mes por partición, y Oracle crea las que falten (INTERVAL) =====
CREATE TABLE ventas_part
PARTITION BY RANGE (fecha_venta) INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
  (PARTITION p_antes_2024 VALUES LESS THAN (DATE '2024-01-01'))
TABLESPACE ts_sgejd_ventas
AS SELECT id, cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion FROM ventas;
ALTER TABLE ventas_part ADD CONSTRAINT pk_ventas_part PRIMARY KEY (id);
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS_PART', cascade => TRUE);

SELECT COUNT(*) AS particiones, MIN(num_rows) AS filas_minimo, MAX(num_rows) AS filas_maximo, SUM(num_rows) AS filas_total
FROM user_tab_partitions WHERE table_name = 'VENTAS_PART';
SELECT partition_name, partition_position, num_rows, high_value FROM user_tab_partitions
WHERE table_name = 'VENTAS_PART' AND partition_position IN (1, 2, 3, 34, 35)
ORDER BY partition_position;

PROMPT ===== 2) Pruning: ventas de octubre 2026 (rango explícito) =====
EXPLAIN PLAN SET STATEMENT_ID = 'RANGO' FOR
SELECT estado, COUNT(*) FROM ventas_part
WHERE fecha_venta >= DATE '2026-10-01' AND fecha_venta < DATE '2026-11-01' GROUP BY estado;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'RANGO', 'BASIC +PARTITION +COST +ROWS'));

PROMPT ===== 3) La misma pregunta con la clave de partición dentro de una función =====
EXPLAIN PLAN SET STATEMENT_ID = 'TRUNC' FOR
SELECT estado, COUNT(*) FROM ventas_part
WHERE TRUNC(fecha_venta, 'MM') = DATE '2026-10-01' GROUP BY estado;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'TRUNC', 'BASIC +PARTITION +COST +ROWS'));

PROMPT ===== 4) Y la forma en que lo escribiría un reporte "por año" con TO_CHAR =====
EXPLAIN PLAN SET STATEMENT_ID = 'TOCHAR' FOR
SELECT estado, COUNT(*) FROM ventas_part
WHERE TO_CHAR(fecha_venta, 'YYYY') = '2025' GROUP BY estado;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'TOCHAR', 'BASIC +PARTITION +COST +ROWS'));
EXPLAIN PLAN SET STATEMENT_ID = 'ANIO' FOR
SELECT estado, COUNT(*) FROM ventas_part
WHERE fecha_venta >= DATE '2025-01-01' AND fecha_venta < DATE '2026-01-01' GROUP BY estado;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'ANIO', 'BASIC +PARTITION +COST +ROWS'));

PROMPT ===== 5) Resumen =====
column statement_id format a8
SELECT p.statement_id, p.cost,
       (SELECT MIN(partition_start) || ' / ' || MIN(partition_stop) FROM plan_table x
        WHERE x.statement_id = p.statement_id AND x.operation LIKE 'PARTITION%') AS pstart_pstop,
       (SELECT MIN(operation || ' ' || options) FROM plan_table x
        WHERE x.statement_id = p.statement_id AND x.operation LIKE 'PARTITION%') AS operacion
FROM plan_table p WHERE p.id = 0 ORDER BY p.statement_id;
DELETE FROM plan_table;
COMMIT;
exit
