-- ============================================================
-- BD2 · S4 · Paso 2: los tres planes de la consulta de rentabilidad. Como JDREFRIGERACION, justo después
-- de S04_01_volumen.sql:   .\bd2\herramientas\sql.ps1 bd2\S04\S04_02_planes.sql
--
-- Consulta real: VentaRepository.lineasVendidas (reporte de Rentabilidad, RentabilidadService). Líneas de
-- las ventas NO anuladas de un periodo, con su venta, cliente, producto y categoría. Periodo: octubre 2026.
--
-- Momento A: SQL tal como lo genera Hibernate desde el JPQL, con estadísticas viejas.
-- Momento B: la misma consulta, después de DBMS_STATS.
-- Momento C: la consulta reescrita (dos buenas prácticas):
--   1) sin filtros opcionales "(:desde IS NULL OR fecha >= :desde)": la condición queda fija;
--   2) estado = 'REGISTRADA' en vez de estado <> 'ANULADA' (igualdad en vez de negación; equivalente
--      porque la CHECK de VENTAS solo admite esos dos valores).
-- Sin índices nuevos: eso es S5.
-- ============================================================
set echo off feedback off linesize 200 pagesize 200 serveroutput on
VARIABLE desde VARCHAR2(10)
VARIABLE hasta VARCHAR2(10)
EXEC :desde := '2026-10-01'; :hasta := '2026-10-31';

PROMPT ================= MOMENTO A: consulta de Hibernate, estadísticas viejas =================
EXPLAIN PLAN SET STATEMENT_ID = 'A' FOR
SELECT d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado <> 'ANULADA'
  AND (:desde IS NULL OR v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD'))
  AND (:hasta IS NULL OR v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD'))
ORDER BY v.fecha_venta, v.id;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'A', 'TYPICAL'));

PROMPT ================= Actualizar estadísticas (DBMS_STATS) =================
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'DETALLE_VENTA');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'PRODUCTOS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'CLIENTES');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'CATEGORIAS');
column table_name format a15
SELECT table_name, num_rows, TO_CHAR(last_analyzed, 'DD/MM HH24:MI') AS analizada, stale_stats
FROM user_tab_statistics WHERE table_name IN ('VENTAS', 'DETALLE_VENTA') ORDER BY 1;

PROMPT ================= MOMENTO B: la misma consulta, estadísticas al día =================
EXPLAIN PLAN SET STATEMENT_ID = 'B' FOR
SELECT d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado <> 'ANULADA'
  AND (:desde IS NULL OR v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD'))
  AND (:hasta IS NULL OR v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD'))
ORDER BY v.fecha_venta, v.id;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'B', 'TYPICAL'));

PROMPT ================= MOMENTO C: consulta reescrita, estadísticas al día =================
EXPLAIN PLAN SET STATEMENT_ID = 'C' FOR
SELECT d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado = 'REGISTRADA'
  AND v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD')
  AND v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD')
ORDER BY v.fecha_venta, v.id;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'C', 'TYPICAL'));

PROMPT ================= Resumen de los tres momentos (fila 0 de cada plan) =================
column statement_id format a3 heading MOM
SELECT statement_id, cost, cardinality AS filas_estimadas, bytes
FROM plan_table WHERE id = 0 AND statement_id IN ('A', 'B', 'C') ORDER BY statement_id;

PROMPT ================= ¿Cuántas filas devuelve de verdad? (las dos versiones deben coincidir) =================
SELECT 'Hibernate (A/B)' AS version, COUNT(*) AS filas, SUM(d.cantidad) AS unidades,
       SUM(d.precio_unitario * d.cantidad) AS importe
FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
JOIN clientes c ON c.id = v.cliente_id JOIN productos p ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado <> 'ANULADA'
  AND (:desde IS NULL OR v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD'))
  AND (:hasta IS NULL OR v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD'))
UNION ALL
SELECT 'Reescrita (C)', COUNT(*), SUM(d.cantidad), SUM(d.precio_unitario * d.cantidad)
FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
JOIN clientes c ON c.id = v.cliente_id JOIN productos p ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado = 'REGISTRADA'
  AND v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD')
  AND v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD');

DELETE FROM plan_table WHERE statement_id IN ('A', 'B', 'C');
COMMIT;
exit
