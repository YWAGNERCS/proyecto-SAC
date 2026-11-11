-- BD2 · S10 · Paso 3b: ejecuta de verdad la consulta de Rentabilidad (octubre 2026), para que V$SQL la vea.
--   .\bd2\herramientas\sql.ps1 bd2\S10\S10_03b_ejecutar_consulta.sql
set echo off feedback on termout on serveroutput on
DECLARE
    TYPE t_ids IS TABLE OF NUMBER;
    v_ids t_ids;
BEGIN
    FOR i IN 1 .. 5 LOOP
        SELECT /*+ GATHER_PLAN_STATISTICS S10_RENTABILIDAD */ d.id BULK COLLECT INTO v_ids
        FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id JOIN clientes c ON c.id = v.cliente_id
        JOIN productos p ON p.id = d.producto_id LEFT JOIN categorias cat ON cat.id = p.categoria_id
        WHERE v.estado = 'REGISTRADA' AND v.fecha_venta >= DATE '2026-10-01' AND v.fecha_venta <= DATE '2026-10-31'
        ORDER BY v.fecha_venta, v.id;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Filas de octubre: ' || v_ids.COUNT);
END;
/
exit
