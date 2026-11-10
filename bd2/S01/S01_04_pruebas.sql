-- ============================================================
-- BD2 · S1 · Paso 4: pruebas del motor transaccional.
-- Se ejecuta como el propietario JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S01\S01_04_pruebas.sql
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 serveroutput on

PROMPT === Limpieza: deshace las ventas de prueba de una corrida anterior (devuelve su stock) ===
BEGIN
    FOR l IN (SELECT d.producto_id, d.cantidad FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
              WHERE v.vendedor_nombre = 'Prueba BD2 S1') LOOP
        UPDATE productos SET stock_actual = stock_actual + l.cantidad
        WHERE id = l.producto_id AND sector = 'MATERIAL_SERVICIO';
    END LOOP;
    DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S1');
    DELETE FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S1';
    COMMIT;
END;
/

PROMPT === Prueba 1: la función reproduce el total que calculó el backend Java ===
PROMPT (se compara FN_TOTAL_VENTA con COMPROBANTES.TOTAL, guardado por LP2, en todas las ventas)
SELECT COUNT(*)                                                      AS ventas_comparadas,
       SUM(CASE WHEN fn_total_venta(v.id) = c.total THEN 1 ELSE 0 END) AS coinciden,
       SUM(CASE WHEN fn_total_venta(v.id) <> c.total THEN 1 ELSE 0 END) AS difieren
FROM ventas v
JOIN comprobantes c ON c.venta_id = v.id;

PROMPT === Prueba 2: caso válido (venta al contado de material con stock) ===
VARIABLE v_cliente  NUMBER
VARIABLE v_producto NUMBER
BEGIN
    SELECT MIN(id) INTO :v_cliente FROM clientes;
    -- material en soles con stock y precio bajo (para no pasar los 700 soles)
    SELECT id INTO :v_producto FROM (
        SELECT id FROM productos
        WHERE sector = 'MATERIAL_SERVICIO' AND moneda = 'SOLES' AND stock_actual >= 5
        ORDER BY costo_base * (1 + margen_porcentaje / 100), id
    ) WHERE ROWNUM = 1;
END;
/
SELECT id, nombre, stock_actual AS stock_antes,
       ROUND(costo_base * (1 + margen_porcentaje / 100), 2) AS precio
FROM productos WHERE id = :v_producto;

DECLARE
    v_venta_id NUMBER;
    v_total    NUMBER;
BEGIN
    sp_registrar_venta(
        p_cliente_id  => :v_cliente,
        p_producto_id => :v_producto,
        p_cantidad    => 3,
        p_descuento   => 5,
        p_rol         => 'ASISTENTE',
        p_vendedor    => 'Prueba BD2 S1',
        p_venta_id    => v_venta_id,
        p_total       => v_total);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Venta registrada: ' || v_venta_id || ' | total devuelto (OUT): ' || v_total
                         || ' | total recalculado por FN_TOTAL_VENTA: ' || fn_total_venta(v_venta_id));
END;
/

PROMPT >> La venta quedó en las tablas, con su línea, y el stock bajó en 3
SELECT v.id, v.fecha_venta, v.tipo_pago, v.estado_pago, v.estado, v.vendedor_nombre,
       d.producto_id, d.cantidad, d.precio_unitario, d.descuento_porcentaje,
       fn_subtotal_linea(d.cantidad, d.precio_unitario, d.descuento_porcentaje) AS subtotal_linea,
       fn_total_venta(v.id) AS total_con_igv
FROM ventas v JOIN detalle_venta d ON d.venta_id = v.id
WHERE v.id = (SELECT MAX(id) FROM ventas);
SELECT id, stock_actual AS stock_despues FROM productos WHERE id = :v_producto;

PROMPT === Prueba 3: casos que deben rechazarse (y no dejan nada a medias) ===
DECLARE
    v_venta_id NUMBER;
    v_total    NUMBER;
    PROCEDURE probar(p_nombre VARCHAR2, p_producto NUMBER, p_cantidad NUMBER, p_descuento NUMBER, p_rol VARCHAR2) IS
    BEGIN
        sp_registrar_venta(:v_cliente, p_producto, p_cantidad, p_descuento, p_rol, 'Prueba BD2 S1', v_venta_id, v_total);
        DBMS_OUTPUT.PUT_LINE('FALLO: ' || p_nombre || ' se aceptó');
        ROLLBACK;
    EXCEPTION WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('Rechazado (' || p_nombre || '): ' || SQLERRM);
    END;
BEGIN
    probar('descuento sobre el tope del asistente', :v_producto, 1, 15, 'ASISTENTE');
    probar('más unidades que el stock',            :v_producto, 100000, 0, 'ADMIN');
    probar('producto inexistente',                 -1, 1, 0, 'ADMIN');
END;
/
PROMPT >> La CHECK nueva también protege a quien escriba SQL directo, sin pasar por el procedimiento
UPDATE productos SET stock_actual = -1 WHERE id = :v_producto;
ROLLBACK;
SELECT COUNT(*) AS ventas_de_prueba FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S1';
exit
