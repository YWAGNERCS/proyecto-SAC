-- ============================================================
-- BD2 · S2 · Paso 3: pruebas de los triggers. Se ejecuta como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S02\S02_03_pruebas.sql
-- Usa SP_REGISTRAR_VENTA de S1. Se puede repetir: empieza borrando las ventas de prueba anteriores.
-- ============================================================
set echo off feedback on linesize 220 pagesize 100 serveroutput on
column accion format a7
column estado_anterior format a11
column estado_nuevo format a11
column estado_pago_anterior format a9 heading PAGO_ANT
column estado_pago_nuevo format a9 heading PAGO_NUEVO
column anulado_por format a10
column motivo format a32
column programa format a26
column usuario_oracle format a16

PROMPT === Limpieza de corridas anteriores (sus DELETE también quedan auditados) ===
BEGIN
    FOR l IN (SELECT d.producto_id, d.cantidad, v.estado FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
              WHERE v.vendedor_nombre = 'Prueba BD2 S2') LOOP
        IF l.estado = 'REGISTRADA' THEN   -- una anulada ya devolvió su stock
            UPDATE productos SET stock_actual = stock_actual + l.cantidad
            WHERE id = l.producto_id AND sector = 'MATERIAL_SERVICIO';
        END IF;
    END LOOP;
    DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S2');
    DELETE FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S2';
    COMMIT;
END;
/

VARIABLE v_venta    NUMBER
VARIABLE v_venta2   NUMBER
VARIABLE v_producto NUMBER
VARIABLE v_desde    NUMBER
BEGIN
    SELECT NVL(MAX(id_auditoria), 0) INTO :v_desde FROM venta_auditoria;
    SELECT id INTO :v_producto FROM (
        SELECT id FROM productos WHERE sector = 'MATERIAL_SERVICIO' AND moneda = 'SOLES' AND stock_actual >= 5
        ORDER BY costo_base * (1 + margen_porcentaje / 100), id) WHERE ROWNUM = 1;
END;
/

PROMPT === Caso válido 1: registrar dos ventas (INSERT auditado) ===
DECLARE
    v_total NUMBER;
    v_cliente NUMBER;
BEGIN
    SELECT MIN(id) INTO v_cliente FROM clientes;
    sp_registrar_venta(v_cliente, :v_producto, 2, 0, 'ADMIN', 'Prueba BD2 S2', :v_venta, v_total);
    sp_registrar_venta(v_cliente, :v_producto, 1, 0, 'ADMIN', 'Prueba BD2 S2', :v_venta2, v_total);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Ventas de prueba: ' || :v_venta || ' y ' || :v_venta2);
END;
/

PROMPT === Caso válido 2: anular con motivo y quién (como VentaService.anular, que devuelve el stock) ===
UPDATE productos SET stock_actual = stock_actual + 2 WHERE id = :v_producto;
UPDATE ventas
SET estado = 'ANULADA', anulado_por = 'lucy', motivo_anulacion = 'Cliente desistió de la compra'
WHERE id = :v_venta;
COMMIT;
SELECT id, estado, fecha_anulacion, anulado_por, motivo_anulacion FROM ventas WHERE id = :v_venta;
PROMPT (FECHA_ANULACION no se envió en el UPDATE: la puso el trigger BEFORE)

PROMPT === Caso inválido 1: volver a REGISTRADA una venta anulada ===
UPDATE ventas SET estado = 'REGISTRADA' WHERE id = :v_venta;

PROMPT === Caso inválido 2: anular sin motivo ===
UPDATE ventas SET estado = 'ANULADA', anulado_por = 'lucy' WHERE id = :v_venta2;

PROMPT === Caso inválido 3: anular sin decir quién ===
UPDATE ventas SET estado = 'ANULADA', motivo_anulacion = 'Error de digitación' WHERE id = :v_venta2;
ROLLBACK;

PROMPT === Un UPDATE que no toca ESTADO no dispara la regla (BEFORE UPDATE OF estado), pero sí la auditoría ===
UPDATE ventas SET estado_pago = 'PENDIENTE' WHERE id = :v_venta2;
UPDATE ventas SET estado_pago = 'PAGADO' WHERE id = :v_venta2;
COMMIT;

PROMPT === Auditoría generada por esta corrida ===
SELECT id_venta, accion, estado_anterior, estado_nuevo, estado_pago_anterior, estado_pago_nuevo,
       anulado_por, motivo, usuario_oracle, programa, TO_CHAR(fecha_evento, 'DD/MM HH24:MI:SS') AS fecha
FROM venta_auditoria
WHERE id_auditoria > :v_desde
ORDER BY id_auditoria;

PROMPT === Los rechazados no dejaron rastro: el BEFORE abortó la sentencia antes del AFTER ===
SELECT COUNT(*) AS filas_con_regreso_a_registrada
FROM venta_auditoria
WHERE id_auditoria > :v_desde AND estado_anterior = 'ANULADA' AND estado_nuevo = 'REGISTRADA';
exit
