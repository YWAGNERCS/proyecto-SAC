-- ============================================================
-- BD2 · S5 · Paso 2: selectividad de las columnas candidatas, y de los predicados reales.
--   .\bd2\herramientas\sql.ps1 bd2\S05\S05_02_selectividad.sql
-- Selectividad de la columna = valores distintos / filas. Selectividad del predicado = filas que devuelve
-- el WHERE concreto / filas. El índice lo decide la segunda; la primera solo orienta.
-- ============================================================
set echo off feedback off linesize 200 pagesize 100
column columna format a38
column predicado format a62

PROMPT === Selectividad de cada columna ===
SELECT 'VENTAS.FECHA_VENTA' AS columna, COUNT(DISTINCT fecha_venta) AS distintos, COUNT(*) AS filas,
       ROUND(COUNT(DISTINCT fecha_venta) / COUNT(*), 4) AS selectividad FROM ventas
UNION ALL SELECT 'VENTAS.ESTADO', COUNT(DISTINCT estado), COUNT(*), ROUND(COUNT(DISTINCT estado) / COUNT(*), 6) FROM ventas
UNION ALL SELECT 'VENTAS.MONEDA', COUNT(DISTINCT moneda), COUNT(*), ROUND(COUNT(DISTINCT moneda) / COUNT(*), 6) FROM ventas
UNION ALL SELECT 'DETALLE_VENTA.VENTA_ID', COUNT(DISTINCT venta_id), COUNT(*), ROUND(COUNT(DISTINCT venta_id) / COUNT(*), 4) FROM detalle_venta
UNION ALL SELECT 'COBROS.REFERENCIA (tal cual)', COUNT(DISTINCT referencia), COUNT(*), ROUND(COUNT(DISTINCT referencia) / COUNT(*), 4) FROM cobros
UNION ALL SELECT 'COBROS.UPPER(TRIM(REFERENCIA))', COUNT(DISTINCT UPPER(TRIM(referencia))), COUNT(*), ROUND(COUNT(DISTINCT UPPER(TRIM(referencia))) / COUNT(*), 4) FROM cobros
UNION ALL SELECT 'VENTA_AUDITORIA.ACCION', COUNT(DISTINCT accion), COUNT(*), ROUND(COUNT(DISTINCT accion) / COUNT(*), 6) FROM venta_auditoria;

PROMPT === Selectividad de los predicados de las consultas reales ===
WITH t AS (SELECT (SELECT COUNT(*) FROM ventas) AS ventas, (SELECT COUNT(*) FROM detalle_venta) AS lineas,
                  (SELECT COUNT(*) FROM cobros) AS cobros, (SELECT COUNT(*) FROM venta_auditoria) AS auditoria FROM dual)
SELECT 'Ventas de un día (hoy): fecha_venta = TRUNC(SYSDATE)' AS predicado, n AS filas, ROUND(n / t.ventas * 100, 3) AS porcentaje
FROM t, (SELECT COUNT(*) n FROM ventas WHERE fecha_venta = TRUNC(SYSDATE))
UNION ALL SELECT 'Rentabilidad de octubre 2026 (S4)', n, ROUND(n / t.ventas * 100, 3)
FROM t, (SELECT COUNT(*) n FROM ventas WHERE fecha_venta BETWEEN DATE '2026-10-01' AND DATE '2026-10-31' AND estado = 'REGISTRADA')
UNION ALL SELECT 'Líneas de una venta: venta_id = 20000', n, ROUND(n / t.lineas * 100, 4)
FROM t, (SELECT COUNT(*) n FROM detalle_venta WHERE venta_id = 20000)
UNION ALL SELECT 'Voucher: UPPER(TRIM(referencia)) = ''OP-01012345''', n, ROUND(n / t.cobros * 100, 4)
FROM t, (SELECT COUNT(*) n FROM cobros WHERE UPPER(TRIM(referencia)) = 'OP-01012345')
UNION ALL SELECT 'Auditoría: accion = ''DELETE''', n, ROUND(n / t.auditoria * 100, 3)
FROM t, (SELECT COUNT(*) n FROM venta_auditoria WHERE accion = 'DELETE')
UNION ALL SELECT 'Ventas registradas: estado = ''REGISTRADA''', n, ROUND(n / t.ventas * 100, 2)
FROM t, (SELECT COUNT(*) n FROM ventas WHERE estado = 'REGISTRADA')
UNION ALL SELECT 'Ventas anuladas: estado = ''ANULADA''', n, ROUND(n / t.ventas * 100, 2)
FROM t, (SELECT COUNT(*) n FROM ventas WHERE estado = 'ANULADA')
UNION ALL SELECT 'Ventas en dólares: moneda = ''DOLARES''', n, ROUND(n / t.ventas * 100, 2)
FROM t, (SELECT COUNT(*) n FROM ventas WHERE moneda = 'DOLARES');

PROMPT === ¿Cuántos vouchers no se encontrarían comparando tal cual? (por qué la consulta usa UPPER(TRIM)) ===
SELECT COUNT(*) AS escritos_distinto FROM cobros WHERE referencia <> UPPER(TRIM(referencia));
exit
