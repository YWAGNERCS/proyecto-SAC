-- ============================================================
-- BD2 · S10 · Optimización del rendimiento · SGE-JD
-- Paso 1: volumen de prueba EN ORDEN DE FECHA, más los índices de S5. Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S10\S10_01_volumen_ordenado.sql
--
-- En S4 la carga puso fechas al azar sobre IDs consecutivos, y S5 midió el resultado: CLUSTERING_FACTOR de
-- IX_VENTAS_FECHA_VENTA casi igual al número de filas, y el índice inútil para un mes. En JD las ventas
-- entran en el orden en que ocurren, así que esta carga recorre los días del 01/01/2024 a hoy y registra
-- entre 20 y 40 ventas por día laborable (unas 30 000 en total), con 1 a 5 líneas cada una.
-- Antes de cargar se toman estadísticas con la base chica (como en S4), para que el paso 2 parta de
-- estadísticas viejas. La auditoría de S2 (trigger) y la de S9 (POL_SGEJD_VENTAS) se apagan solo durante la
-- carga: no es actividad del negocio.
-- ============================================================
set echo off feedback on serveroutput on timing on

EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'DETALLE_VENTA');

ALTER TRIGGER trg_venta_auditoria DISABLE;
DECLARE
    TYPE t_ids IS TABLE OF NUMBER;
    v_clientes  t_ids;
    v_productos t_ids;
    v_precios   t_ids;
    v_venta     NUMBER;
    v_dia       DATE := DATE '2024-01-01';
    v_n         PLS_INTEGER;
    v_cliente   NUMBER;
    v_estado    VARCHAR2(10);
    v_tipo      VARCHAR2(10);
    v_k         PLS_INTEGER;
    v_total     PLS_INTEGER := 0;
BEGIN
    SELECT id BULK COLLECT INTO v_clientes FROM clientes ORDER BY id;
    SELECT id, ROUND(costo_base * (1 + margen_porcentaje / 100), 2)
    BULK COLLECT INTO v_productos, v_precios FROM productos WHERE moneda = 'SOLES' ORDER BY id;
    DBMS_RANDOM.SEED(2028);
    WHILE v_dia <= TRUNC(SYSDATE) LOOP
        IF TO_CHAR(v_dia, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH') <> 'SUN' THEN
            v_n := TRUNC(DBMS_RANDOM.VALUE(20, 41));
            FOR i IN 1 .. v_n LOOP
                v_cliente := v_clientes(TRUNC(DBMS_RANDOM.VALUE(1, v_clientes.COUNT + 1)));
                v_estado  := CASE WHEN DBMS_RANDOM.VALUE < 0.03 THEN 'ANULADA' ELSE 'REGISTRADA' END;
                v_tipo    := CASE WHEN DBMS_RANDOM.VALUE < 0.7 THEN 'CONTADO' ELSE 'CREDITO' END;
                INSERT INTO ventas (cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion,
                                    vendedor_nombre)
                VALUES (v_cliente, v_dia, 'SOLES', v_tipo,
                        CASE WHEN v_tipo = 'CONTADO' OR v_dia < TRUNC(SYSDATE) - 30 THEN 'PAGADO' ELSE 'PENDIENTE' END,
                        v_estado, 'NINGUNA', 'Carga BD2 S10')
                RETURNING id INTO v_venta;
                FOR j IN 1 .. TRUNC(DBMS_RANDOM.VALUE(1, 6)) LOOP
                    v_k := TRUNC(DBMS_RANDOM.VALUE(1, v_productos.COUNT + 1));
                    INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, descuento_porcentaje,
                                               costo_unitario, precio_catalogo)
                    VALUES (v_venta, v_productos(v_k), TRUNC(DBMS_RANDOM.VALUE(1, 6)), v_precios(v_k), 0,
                            ROUND(v_precios(v_k) / 1.25, 2), v_precios(v_k));
                END LOOP;
                v_total := v_total + 1;
            END LOOP;
            COMMIT;
        END IF;
        v_dia := v_dia + 1;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Ventas cargadas: ' || v_total);
END;
/
ALTER TRIGGER trg_venta_auditoria ENABLE;

-- Índices de S5 (el de fecha y la FK de las líneas), ahora en el tablespace de S9
CREATE INDEX ix_ventas_fecha_venta  ON ventas (fecha_venta)        TABLESPACE ts_sgejd_ventas;
CREATE INDEX ix_detalle_venta_venta ON detalle_venta (venta_id)     TABLESPACE ts_sgejd_ventas;
exit
