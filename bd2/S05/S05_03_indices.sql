-- ============================================================
-- BD2 · S5 · Paso 3: cada índice con su EXPLAIN PLAN antes y después. Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S05\S05_03_indices.sql
-- Se usan literales en las consultas: EXPLAIN PLAN no mira el valor de las variables (hallazgo de S4).
-- Se puede repetir: empieza borrando los índices de una corrida anterior.
-- ============================================================
set echo off feedback off linesize 200 pagesize 200
DROP INDEX IF EXISTS ix_ventas_fecha_venta;
DROP INDEX IF EXISTS ix_detalle_venta_venta;
DROP INDEX IF EXISTS ix_cobros_referencia;
DROP INDEX IF EXISTS ix_cobros_referencia_norm;
DROP INDEX IF EXISTS bx_venta_auditoria_accion;
DROP INDEX IF EXISTS ix_ventas_estado;
DELETE FROM plan_table;

PROMPT
PROMPT ############ 1. B-Tree: DETALLE_VENTA(VENTA_ID) — líneas de una venta ############
PROMPT (clave foránea sin índice; la usa Hibernate cada vez que carga el detalle de una venta)
EXPLAIN PLAN SET STATEMENT_ID = '1A' FOR
SELECT * FROM detalle_venta WHERE venta_id = 20000;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '1A', 'BASIC +ROWS +COST +PREDICATE'));
CREATE INDEX ix_detalle_venta_venta ON detalle_venta (venta_id);
EXPLAIN PLAN SET STATEMENT_ID = '1B' FOR
SELECT * FROM detalle_venta WHERE venta_id = 20000;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '1B', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ############ 2. B-Tree: VENTAS(FECHA_VENTA) — ventas de un día y rentabilidad de un mes ############
EXPLAIN PLAN SET STATEMENT_ID = '2A' FOR
SELECT v.id, v.cliente_id, d.producto_id, d.cantidad, d.precio_unitario
FROM ventas v JOIN detalle_venta d ON d.venta_id = v.id
WHERE v.estado = 'REGISTRADA' AND v.fecha_venta >= TRUNC(SYSDATE) AND v.fecha_venta <= TRUNC(SYSDATE);
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '2A', 'BASIC +ROWS +COST +PREDICATE'));
CREATE INDEX ix_ventas_fecha_venta ON ventas (fecha_venta);
PROMPT >> un día (hoy), con el índice
EXPLAIN PLAN SET STATEMENT_ID = '2B' FOR
SELECT v.id, v.cliente_id, d.producto_id, d.cantidad, d.precio_unitario
FROM ventas v JOIN detalle_venta d ON d.venta_id = v.id
WHERE v.estado = 'REGISTRADA' AND v.fecha_venta >= TRUNC(SYSDATE) AND v.fecha_venta <= TRUNC(SYSDATE);
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '2B', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT >> un mes (octubre, la consulta reescrita de S4), con el índice
EXPLAIN PLAN SET STATEMENT_ID = '2C' FOR
SELECT v.id, v.cliente_id, d.producto_id, d.cantidad, d.precio_unitario
FROM ventas v JOIN detalle_venta d ON d.venta_id = v.id
WHERE v.estado = 'REGISTRADA' AND v.fecha_venta >= DATE '2026-10-01' AND v.fecha_venta <= DATE '2026-10-31';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '2C', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT >> la versión de Hibernate de S4 (IS NULL OR), con el mismo índice
EXPLAIN PLAN SET STATEMENT_ID = '2D' FOR
SELECT v.id, v.cliente_id, d.producto_id, d.cantidad, d.precio_unitario
FROM ventas v JOIN detalle_venta d ON d.venta_id = v.id
WHERE v.estado <> 'ANULADA'
  AND (:desde IS NULL OR v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD'))
  AND (:hasta IS NULL OR v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD'));
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '2D', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT >> por qué el mes no usa el índice: factor de agrupamiento (clustering factor)
column index_name format a24
SELECT i.index_name, i.leaf_blocks, i.distinct_keys, i.clustering_factor, t.blocks AS bloques_tabla, t.num_rows AS filas_tabla
FROM user_indexes i JOIN user_tables t ON t.table_name = i.table_name
WHERE i.index_name IN ('IX_VENTAS_FECHA_VENTA', 'IX_DETALLE_VENTA_VENTA');

PROMPT
PROMPT ############ 3. Function-Based: COBROS(UPPER(TRIM(REFERENCIA))) — voucher duplicado ############
PROMPT (CobroRepository.vigentesConReferencia: corre en CADA cobro registrado)
EXPLAIN PLAN SET STATEMENT_ID = '3A' FOR
SELECT * FROM cobros
WHERE estado = 'VIGENTE' AND metodo_pago = 'TRANSFERENCIA' AND UPPER(TRIM(referencia)) = UPPER(TRIM(' op-01012345 '));
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '3A', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT >> primero un B-Tree común sobre REFERENCIA (lo que se haría "por intuición")
CREATE INDEX ix_cobros_referencia ON cobros (referencia);
EXPLAIN PLAN SET STATEMENT_ID = '3B' FOR
SELECT * FROM cobros
WHERE estado = 'VIGENTE' AND metodo_pago = 'TRANSFERENCIA' AND UPPER(TRIM(referencia)) = UPPER(TRIM(' op-01012345 '));
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '3B', 'BASIC +ROWS +COST +PREDICATE'));
DROP INDEX ix_cobros_referencia;
PROMPT >> ahora el índice sobre la expresión que la consulta realmente usa
CREATE INDEX ix_cobros_referencia_norm ON cobros (UPPER(TRIM(referencia)));
EXPLAIN PLAN SET STATEMENT_ID = '3C' FOR
SELECT * FROM cobros
WHERE estado = 'VIGENTE' AND metodo_pago = 'TRANSFERENCIA' AND UPPER(TRIM(referencia)) = UPPER(TRIM(' op-01012345 '));
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '3C', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ############ 4. Bitmap: VENTA_AUDITORIA(ACCION) — "¿quién borró ventas?" ############
EXPLAIN PLAN SET STATEMENT_ID = '4A' FOR
SELECT id_venta, estado_anterior, usuario_oracle, programa, fecha_evento FROM venta_auditoria WHERE accion = 'DELETE';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '4A', 'BASIC +ROWS +COST +PREDICATE'));
CREATE BITMAP INDEX bx_venta_auditoria_accion ON venta_auditoria (accion);
EXPLAIN PLAN SET STATEMENT_ID = '4B' FOR
SELECT id_venta, estado_anterior, usuario_oracle, programa, fecha_evento FROM venta_auditoria WHERE accion = 'DELETE';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '4B', 'BASIC +ROWS +COST +PREDICATE'));
EXPLAIN PLAN SET STATEMENT_ID = '4C' FOR
SELECT accion, COUNT(*) FROM venta_auditoria GROUP BY accion;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '4C', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT
PROMPT ############ 5. El que NO conviene: VENTAS(ESTADO) ############
CREATE INDEX ix_ventas_estado ON ventas (estado);
PROMPT >> estado = 'REGISTRADA' (97 % de las filas)
EXPLAIN PLAN SET STATEMENT_ID = '5A' FOR SELECT * FROM ventas WHERE estado = 'REGISTRADA';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '5A', 'BASIC +ROWS +COST +PREDICATE'));
PROMPT >> estado = 'ANULADA' (3 % de las filas)
EXPLAIN PLAN SET STATEMENT_ID = '5B' FOR SELECT * FROM ventas WHERE estado = 'ANULADA';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, '5B', 'BASIC +ROWS +COST +PREDICATE'));
column column_name format a12
SELECT column_name, num_distinct, histogram FROM user_tab_col_statistics WHERE table_name = 'VENTAS' AND column_name IN ('ESTADO', 'MONEDA');
DROP INDEX ix_ventas_estado;

PROMPT
PROMPT ############ Resumen: índices que quedan ############
column index_type format a22
SELECT index_name, index_type, table_name, uniqueness FROM user_indexes
WHERE index_name IN ('IX_DETALLE_VENTA_VENTA', 'IX_VENTAS_FECHA_VENTA', 'IX_COBROS_REFERENCIA_NORM', 'BX_VENTA_AUDITORIA_ACCION')
ORDER BY table_name;
DELETE FROM plan_table;
COMMIT;
exit
