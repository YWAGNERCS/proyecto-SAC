-- ============================================================
-- BD2 · S4 · Optimización de consultas · SGE-JD
-- Paso 1: volumen de prueba. Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S04\S04_01_volumen.sql
--
-- La copia de desarrollo tiene 237 ventas de un año: con eso cualquier plan responde igual. Se cargan
-- 30 000 ventas más (unos 3 años de JD a un ritmo alto) con 1 a 5 líneas cada una, usando los clientes y
-- productos reales. Todas llevan VENDEDOR_NOMBRE = 'Carga BD2 S4' para poder borrarlas.
--
-- Para que la prueba se pueda repetir y sea realista: primero se borra la carga anterior y se toman
-- estadísticas con la base chica (lo que pasa en producción cuando las estadísticas se tomaron al inicio y
-- después la tabla creció). Así el "antes" del paso 2 siempre parte de estadísticas viejas.
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 timing on
column table_name format a15

PROMPT === Borrar una carga anterior (si existe) ===
ALTER TRIGGER trg_venta_auditoria DISABLE;
DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM ventas WHERE vendedor_nombre = 'Carga BD2 S4');
DELETE FROM ventas WHERE vendedor_nombre = 'Carga BD2 S4';
COMMIT;

PROMPT === Estadísticas con la base chica (como en producción al instalar) ===
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'DETALLE_VENTA');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'PRODUCTOS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'CLIENTES');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'CATEGORIAS');

PROMPT === Carga: 30 000 ventas entre el 01/01/2024 y hoy, 1 a 5 líneas cada una ===
-- El trigger de auditoría (S2) se desactiva solo durante la carga: no es actividad del negocio.
DECLARE
    TYPE t_ids IS TABLE OF NUMBER;
    v_clientes  t_ids;
    v_productos t_ids;
    v_precios   t_ids;
    v_venta     NUMBER;
    v_dias      NUMBER := TRUNC(SYSDATE) - DATE '2024-01-01';
    -- Los valores al azar se calculan en PL/SQL: dentro de un INSERT no se puede usar .COUNT de una colección
    v_cliente   NUMBER;
    v_fecha     DATE;
    v_tipo_pago VARCHAR2(10);
    v_estado    VARCHAR2(10);
    v_lineas    PLS_INTEGER;
    v_k         PLS_INTEGER;
    v_cantidad  PLS_INTEGER;
BEGIN
    SELECT id BULK COLLECT INTO v_clientes FROM clientes ORDER BY id;
    SELECT id, ROUND(costo_base * (1 + margen_porcentaje / 100), 2)
    BULK COLLECT INTO v_productos, v_precios FROM productos WHERE moneda = 'SOLES' ORDER BY id;
    DBMS_RANDOM.SEED(2026);   -- misma carga en cada corrida
    FOR i IN 1 .. 30000 LOOP
        v_cliente   := v_clientes(TRUNC(DBMS_RANDOM.VALUE(1, v_clientes.COUNT + 1)));
        v_fecha     := DATE '2024-01-01' + TRUNC(DBMS_RANDOM.VALUE(0, v_dias + 1));
        v_tipo_pago := CASE WHEN DBMS_RANDOM.VALUE < 0.7 THEN 'CONTADO' ELSE 'CREDITO' END;
        v_estado    := CASE WHEN DBMS_RANDOM.VALUE < 0.03 THEN 'ANULADA' ELSE 'REGISTRADA' END;   -- ~3 %
        INSERT INTO ventas (cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion,
                            vendedor_nombre)
        VALUES (v_cliente, v_fecha, 'SOLES', v_tipo_pago, 'PAGADO', v_estado, 'NINGUNA', 'Carga BD2 S4')
        RETURNING id INTO v_venta;
        v_lineas := TRUNC(DBMS_RANDOM.VALUE(1, 6));
        FOR j IN 1 .. v_lineas LOOP
            v_k        := TRUNC(DBMS_RANDOM.VALUE(1, v_productos.COUNT + 1));
            v_cantidad := TRUNC(DBMS_RANDOM.VALUE(1, 6));
            INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, descuento_porcentaje,
                                       costo_unitario, precio_catalogo)
            VALUES (v_venta, v_productos(v_k), v_cantidad, v_precios(v_k), 0,
                    ROUND(v_precios(v_k) / 1.25, 2), v_precios(v_k));
        END LOOP;
        IF MOD(i, 5000) = 0 THEN COMMIT; END IF;
    END LOOP;
    COMMIT;
END;
/
ALTER TRIGGER trg_venta_auditoria ENABLE;

PROMPT === Volumen real frente a lo que creen las estadísticas ===
SELECT 'VENTAS' AS table_name, (SELECT COUNT(*) FROM ventas) AS filas_reales,
       (SELECT num_rows FROM user_tab_statistics WHERE table_name = 'VENTAS') AS filas_segun_estadisticas FROM dual
UNION ALL
SELECT 'DETALLE_VENTA', (SELECT COUNT(*) FROM detalle_venta),
       (SELECT num_rows FROM user_tab_statistics WHERE table_name = 'DETALLE_VENTA') FROM dual;
SELECT TO_CHAR(fecha_venta, 'YYYY') AS anio, COUNT(*) AS ventas FROM ventas GROUP BY TO_CHAR(fecha_venta, 'YYYY') ORDER BY 1;
exit
