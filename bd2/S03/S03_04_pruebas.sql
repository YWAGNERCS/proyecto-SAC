-- ============================================================
-- BD2 · S3 · Paso 4: un caso válido y uno por cada excepción. Como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S03\S03_04_pruebas.sql
-- (El caso de la fila ocupada necesita dos sesiones: S03_05_bloqueo.ps1)
-- ============================================================
set echo off feedback off linesize 220 pagesize 100 serveroutput on
column objeto format a20
column codigo_error format 999999 heading CODIGO
column mensaje_error format a62 heading MENSAJE
column contexto format a58
column traza format a50

PROMPT === Limpieza de ventas de prueba anteriores ===
BEGIN
    FOR l IN (SELECT d.producto_id, d.cantidad FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
              WHERE v.vendedor_nombre = 'Prueba BD2 S3' AND v.estado = 'REGISTRADA') LOOP
        UPDATE productos SET stock_actual = stock_actual + l.cantidad
        WHERE id = l.producto_id AND sector = 'MATERIAL_SERVICIO';
    END LOOP;
    DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S3');
    DELETE FROM ventas WHERE vendedor_nombre = 'Prueba BD2 S3';
    COMMIT;
END;
/

VARIABLE v_desde    NUMBER
VARIABLE v_cliente  NUMBER
VARIABLE v_producto NUMBER
BEGIN
    SELECT NVL(MAX(id_log), 0) INTO :v_desde FROM log_errores;
    SELECT MIN(id) INTO :v_cliente FROM clientes;
    SELECT id INTO :v_producto FROM (
        SELECT id FROM productos WHERE sector = 'MATERIAL_SERVICIO' AND moneda = 'SOLES' AND stock_actual >= 5
        ORDER BY costo_base * (1 + margen_porcentaje / 100), id) WHERE ROWNUM = 1;
END;
/

PROMPT === Caso válido: no genera ninguna fila en LOG_ERRORES ===
DECLARE
    v_id NUMBER; v_total NUMBER;
BEGIN
    sp_registrar_venta(:v_cliente, :v_producto, 2, 5, 'ASISTENTE', 'Prueba BD2 S3', v_id, v_total);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Venta ' || v_id || ' registrada, total ' || v_total);
END;
/

PROMPT === Casos inválidos (cada uno llamado como lo haría el backend: el error llega al cliente) ===
DECLARE
    v_id NUMBER; v_total NUMBER;
    PROCEDURE probar(p_caso VARCHAR2, p_cliente NUMBER, p_producto NUMBER, p_cantidad NUMBER,
                     p_descuento NUMBER, p_vendedor VARCHAR2) IS
    BEGIN
        sp_registrar_venta(p_cliente, p_producto, p_cantidad, p_descuento, 'ASISTENTE', p_vendedor, v_id, v_total);
        DBMS_OUTPUT.PUT_LINE('FALLO: ' || p_caso || ' se aceptó');
        ROLLBACK;
    EXCEPTION WHEN OTHERS THEN
        ROLLBACK;   -- como Spring: deshace la transacción... y aun así el registro queda
        DBMS_OUTPUT.PUT_LINE(RPAD(p_caso, 34) || SQLERRM);
    END;
BEGIN
    probar('1 NO_DATA_FOUND (cliente):',        -1, :v_producto, 1, 0, 'Prueba BD2 S3');
    probar('2 NO_DATA_FOUND (producto):',       :v_cliente, -1, 1, 0, 'Prueba BD2 S3');
    probar('3 personalizada (tope):',           :v_cliente, :v_producto, 1, 25, 'Prueba BD2 S3');
    probar('4 personalizada (cantidad 0):',     :v_cliente, :v_producto, 0, 0, 'Prueba BD2 S3');
    probar('5 EXCEPTION_INIT ORA-12899:',       :v_cliente, :v_producto, 1, 0, RPAD('Vendedor', 80, 'x'));
END;
/

PROMPT === Caso 6 (predefinida en una función): FN_TOTAL_VENTA de una venta inexistente ===
SELECT fn_total_venta(-1) FROM dual;

PROMPT === Registro de errores de esta corrida (el caso válido no aparece) ===
SELECT objeto, codigo_error, mensaje_error, contexto FROM log_errores
WHERE id_log > :v_desde ORDER BY id_log;

PROMPT === Dónde ocurrió cada uno (FORMAT_ERROR_BACKTRACE) ===
SELECT id_log, objeto, REPLACE(traza, CHR(10), ' | ') AS traza FROM log_errores
WHERE id_log > :v_desde ORDER BY id_log;

PROMPT === Ninguna venta inválida quedó guardada ===
SELECT COUNT(*) AS ventas_de_prueba FROM ventas WHERE vendedor_nombre LIKE 'Prueba BD2 S3%' OR vendedor_nombre LIKE 'Vendedorxx%';
exit
