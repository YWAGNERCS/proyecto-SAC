-- ============================================================
-- BD2 · S10 · Paso 2: ¿estadísticas vigentes?, y el plan de la consulta de Rentabilidad (S4) con los índices
-- de S5. Como JDREFRIGERACION:   .\bd2\herramientas\sql.ps1 bd2\S10\S10_02_estadisticas_y_planes.sql
-- ============================================================
set echo off feedback off linesize 200 pagesize 200
column table_name format a16
column index_name format a24

PROMPT ===== 1) ¿Las estadísticas dicen la verdad? (NUM_ROWS frente al conteo real) =====
SELECT s.table_name, s.num_rows AS filas_segun_estadisticas,
       CASE s.table_name WHEN 'VENTAS' THEN (SELECT COUNT(*) FROM ventas)
                         ELSE (SELECT COUNT(*) FROM detalle_venta) END AS filas_reales,
       TO_CHAR(s.last_analyzed, 'DD/MM HH24:MI') AS analizada, s.stale_stats
FROM user_tab_statistics s WHERE s.table_name IN ('VENTAS', 'DETALLE_VENTA') ORDER BY 1;

PROMPT ===== 2) Actualizarlas, con los índices (CASCADE) =====
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'DETALLE_VENTA', cascade => TRUE);
END;
/
SELECT table_name, num_rows, TO_CHAR(last_analyzed, 'DD/MM HH24:MI') AS analizada, stale_stats
FROM user_tab_statistics WHERE table_name IN ('VENTAS', 'DETALLE_VENTA') ORDER BY 1;

PROMPT ===== 3) Factor de agrupamiento del índice de fecha: carga ordenada (hoy) frente a carga al azar (S5: 28 974) =====
SELECT i.index_name, i.leaf_blocks, i.distinct_keys, i.clustering_factor, t.blocks AS bloques_tabla, t.num_rows AS filas_tabla
FROM user_indexes i JOIN user_tables t ON t.table_name = i.table_name
WHERE i.index_name IN ('IX_VENTAS_FECHA_VENTA', 'IX_DETALLE_VENTA_VENTA');

PROMPT ===== 4) Consulta de Rentabilidad de S4 (versión reescrita), octubre 2026 =====
EXPLAIN PLAN SET STATEMENT_ID = 'MES' FOR
SELECT d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado = 'REGISTRADA'
  AND v.fecha_venta >= DATE '2026-10-01' AND v.fecha_venta <= DATE '2026-10-31'
ORDER BY v.fecha_venta, v.id;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'MES', 'BASIC +ROWS +COST +PREDICATE'));

PROMPT ===== 5) La misma, forzando la lectura completa (para comparar el costo que el CBO descartó) =====
EXPLAIN PLAN SET STATEMENT_ID = 'FULL' FOR
SELECT /*+ FULL(v) FULL(d) */ d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado = 'REGISTRADA'
  AND v.fecha_venta >= DATE '2026-10-01' AND v.fecha_venta <= DATE '2026-10-31'
ORDER BY v.fecha_venta, v.id;
SELECT statement_id, cost, cardinality FROM plan_table WHERE id = 0 AND statement_id IN ('MES', 'FULL');
DELETE FROM plan_table;
COMMIT;
exit
