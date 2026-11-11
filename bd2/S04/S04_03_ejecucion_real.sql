-- ============================================================
-- BD2 · S4 · Paso 3: ejecución REAL de las dos versiones (no solo EXPLAIN PLAN). Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S04\S04_03_ejecucion_real.sql
-- Después, como SYSTEM, S04_04_plan_real.sql muestra el plan que Oracle usó de verdad, con filas estimadas
-- frente a filas reales (GATHER_PLAN_STATISTICS) y los valores de las variables que miró (bind peeking).
--
-- EXPLAIN PLAN no conoce el valor de :desde y :hasta; la ejecución real sí. Cada versión se ejecuta 20 veces
-- trayendo todas sus filas (BULK COLLECT), y se mide el tiempo total.
-- ============================================================
set echo off feedback off serveroutput on linesize 200
DECLARE
    TYPE t_filas IS TABLE OF NUMBER;
    v_ids    t_filas;
    v_desde  VARCHAR2(10) := '2026-10-01';
    v_hasta  VARCHAR2(10) := '2026-10-31';
    v_ini    PLS_INTEGER;
    v_a      PLS_INTEGER;
    v_c      PLS_INTEGER;
    v_filas_a PLS_INTEGER;
    v_filas_c PLS_INTEGER;
BEGIN
    -- Calentamiento: la primera ejecución de cada una carga bloques en memoria; no se mide
    SELECT /*+ GATHER_PLAN_STATISTICS S4_HIBERNATE */ d.id BULK COLLECT INTO v_ids
    FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id JOIN clientes c ON c.id = v.cliente_id
    JOIN productos p ON p.id = d.producto_id LEFT JOIN categorias cat ON cat.id = p.categoria_id
    WHERE v.estado <> 'ANULADA'
      AND (v_desde IS NULL OR v.fecha_venta >= TO_DATE(v_desde, 'YYYY-MM-DD'))
      AND (v_hasta IS NULL OR v.fecha_venta <= TO_DATE(v_hasta, 'YYYY-MM-DD'))
    ORDER BY v.fecha_venta, v.id;
    SELECT /*+ GATHER_PLAN_STATISTICS S4_REESCRITA */ d.id BULK COLLECT INTO v_ids
    FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id JOIN clientes c ON c.id = v.cliente_id
    JOIN productos p ON p.id = d.producto_id LEFT JOIN categorias cat ON cat.id = p.categoria_id
    WHERE v.estado = 'REGISTRADA'
      AND v.fecha_venta >= TO_DATE(v_desde, 'YYYY-MM-DD')
      AND v.fecha_venta <= TO_DATE(v_hasta, 'YYYY-MM-DD')
    ORDER BY v.fecha_venta, v.id;

    v_ini := DBMS_UTILITY.GET_TIME;
    FOR i IN 1 .. 20 LOOP
        SELECT /*+ GATHER_PLAN_STATISTICS S4_HIBERNATE */ d.id BULK COLLECT INTO v_ids
        FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id JOIN clientes c ON c.id = v.cliente_id
        JOIN productos p ON p.id = d.producto_id LEFT JOIN categorias cat ON cat.id = p.categoria_id
        WHERE v.estado <> 'ANULADA'
          AND (v_desde IS NULL OR v.fecha_venta >= TO_DATE(v_desde, 'YYYY-MM-DD'))
          AND (v_hasta IS NULL OR v.fecha_venta <= TO_DATE(v_hasta, 'YYYY-MM-DD'))
        ORDER BY v.fecha_venta, v.id;
    END LOOP;
    v_a := DBMS_UTILITY.GET_TIME - v_ini;
    v_filas_a := v_ids.COUNT;

    v_ini := DBMS_UTILITY.GET_TIME;
    FOR i IN 1 .. 20 LOOP
        SELECT /*+ GATHER_PLAN_STATISTICS S4_REESCRITA */ d.id BULK COLLECT INTO v_ids
        FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id JOIN clientes c ON c.id = v.cliente_id
        JOIN productos p ON p.id = d.producto_id LEFT JOIN categorias cat ON cat.id = p.categoria_id
        WHERE v.estado = 'REGISTRADA'
          AND v.fecha_venta >= TO_DATE(v_desde, 'YYYY-MM-DD')
          AND v.fecha_venta <= TO_DATE(v_hasta, 'YYYY-MM-DD')
        ORDER BY v.fecha_venta, v.id;
    END LOOP;
    v_c := DBMS_UTILITY.GET_TIME - v_ini;
    v_filas_c := v_ids.COUNT;

    DBMS_OUTPUT.PUT_LINE('Hibernate (OR IS NULL, <> ANULADA): ' || v_filas_a || ' filas; 20 ejecuciones en '
                         || v_a * 10 || ' ms (' || ROUND(v_a * 10 / 20, 1) || ' ms cada una)');
    DBMS_OUTPUT.PUT_LINE('Reescrita (rango fijo, = REGISTRADA): ' || v_filas_c || ' filas; 20 ejecuciones en '
                         || v_c * 10 || ' ms (' || ROUND(v_c * 10 / 20, 1) || ' ms cada una)');
END;
/
exit
