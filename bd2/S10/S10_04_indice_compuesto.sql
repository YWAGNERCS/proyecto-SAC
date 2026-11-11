-- ============================================================
-- BD2 · S10 · Paso 4: ¿se justifica un índice compuesto? Se mide antes de crear. Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S10\S10_04_indice_compuesto.sql
-- Consulta real: VentaRepository.buscar(estado, desde, hasta, clienteId), es decir, el historial de UN cliente
-- en un periodo (pantalla de Ventas con filtro de cliente; reporte por cliente).
-- Candidatos: (cliente_id, fecha_venta) y (estado, fecha_venta).
-- ============================================================
set echo off feedback off linesize 200 pagesize 200
column columnas format a28
DROP INDEX IF EXISTS ix_ventas_cliente_fecha;

PROMPT ===== 1) Selectividad de cada columna y de las combinaciones =====
-- (Un COUNT(*) junto a una subconsulta escalar da ORA-00937: el total va en una tabla derivada, como en S5)
WITH t AS (SELECT COUNT(*) AS filas FROM ventas),
     d1 AS (SELECT COUNT(DISTINCT cliente_id) AS cli, COUNT(DISTINCT fecha_venta) AS fec, COUNT(DISTINCT estado) AS est
            FROM ventas),
     d2 AS (SELECT COUNT(*) AS cli_fec FROM (SELECT DISTINCT cliente_id, fecha_venta FROM ventas)),
     d3 AS (SELECT COUNT(*) AS est_fec FROM (SELECT DISTINCT estado, fecha_venta FROM ventas)),
     d  AS (SELECT d1.cli, d1.fec, d1.est, d2.cli_fec, d3.est_fec FROM d1, d2, d3)
SELECT 'cliente_id' AS columnas, d.cli AS distintos, t.filas, ROUND(d.cli / t.filas, 5) AS selectividad FROM t, d
UNION ALL SELECT 'fecha_venta', d.fec, t.filas, ROUND(d.fec / t.filas, 5) FROM t, d
UNION ALL SELECT 'estado', d.est, t.filas, ROUND(d.est / t.filas, 5) FROM t, d
UNION ALL SELECT '(cliente_id, fecha_venta)', d.cli_fec, t.filas, ROUND(d.cli_fec / t.filas, 5) FROM t, d
UNION ALL SELECT '(estado, fecha_venta)', d.est_fec, t.filas, ROUND(d.est_fec / t.filas, 5) FROM t, d;
SELECT COUNT(*) AS indices_sobre_cliente_id FROM user_ind_columns
WHERE table_name = 'VENTAS' AND column_name = 'CLIENTE_ID' AND column_position = 1;

PROMPT ===== 2) Selectividad de los predicados reales (cliente 12, octubre 2026) =====
SELECT 'solo cliente 12' AS columnas, COUNT(*) AS filas FROM ventas WHERE cliente_id = 12
UNION ALL SELECT 'solo octubre', COUNT(*) FROM ventas WHERE fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
UNION ALL SELECT 'cliente 12 y octubre', COUNT(*) FROM ventas
WHERE cliente_id = 12 AND fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31';

PROMPT ===== 3) Plan ANTES (con los índices de una columna) =====
EXPLAIN PLAN SET STATEMENT_ID = 'ANTES' FOR
SELECT v.id, v.fecha_venta, v.estado, v.estado_pago FROM ventas v
WHERE v.cliente_id = 12 AND v.fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
ORDER BY v.fecha_venta DESC, v.id DESC;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'ANTES', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT ===== 4) Índice compuesto: primero la igualdad (cliente), después el rango (fecha) =====
CREATE INDEX ix_ventas_cliente_fecha ON ventas (cliente_id, fecha_venta) TABLESPACE ts_sgejd_ventas;
EXPLAIN PLAN SET STATEMENT_ID = 'DESPUES' FOR
SELECT v.id, v.fecha_venta, v.estado, v.estado_pago FROM ventas v
WHERE v.cliente_id = 12 AND v.fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31'
ORDER BY v.fecha_venta DESC, v.id DESC;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'DESPUES', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT ===== 5) ¿Y sin el cliente? (solo el periodo: el compuesto no debería servir, empieza por cliente) =====
EXPLAIN PLAN SET STATEMENT_ID = 'SOLOFECHA' FOR
SELECT v.id FROM ventas v WHERE v.fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'SOLOFECHA', 'BASIC +ROWS +COST'));

PROMPT ===== 6) Resumen =====
column statement_id format a10
SELECT statement_id, cost, cardinality FROM plan_table WHERE id = 0 ORDER BY statement_id;
DELETE FROM plan_table;
COMMIT;
exit
