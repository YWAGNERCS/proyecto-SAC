-- BD2 · S3 · Sesión B del paso 5 (la lanza S03_05_bloqueo.ps1): intenta vender el producto bloqueado.
set feedback off serveroutput on linesize 200
VARIABLE v_desde NUMBER
EXEC SELECT NVL(MAX(id_log), 0) INTO :v_desde FROM log_errores;
DECLARE
    v_id NUMBER; v_total NUMBER; v_prod NUMBER; v_cli NUMBER;
    v_ini TIMESTAMP := SYSTIMESTAMP;
BEGIN
    SELECT id INTO v_prod FROM (SELECT id FROM productos
                                WHERE sector = 'MATERIAL_SERVICIO' AND moneda = 'SOLES' AND stock_actual >= 5
                                ORDER BY costo_base * (1 + margen_porcentaje / 100), id) WHERE ROWNUM = 1;
    SELECT MIN(id) INTO v_cli FROM clientes;
    DBMS_OUTPUT.PUT_LINE('Sesion B: intenta vender el producto ' || v_prod || ' a las ' || TO_CHAR(v_ini, 'HH24:MI:SS'));
    sp_registrar_venta(v_cli, v_prod, 1, 0, 'ADMIN', 'Prueba BD2 S3', v_id, v_total);
    DBMS_OUTPUT.PUT_LINE('FALLO: la venta se registró sin esperar al bloqueo');
    ROLLBACK;
EXCEPTION WHEN OTHERS THEN
    ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('Sesion B: ' || SQLERRM);
    DBMS_OUTPUT.PUT_LINE('Sesion B: respondió a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS')
        || ' (esperó ' || ROUND(EXTRACT(SECOND FROM (SYSTIMESTAMP - v_ini)), 1) || ' s)');
END;
/
column mensaje_error format a75
column contexto format a52
SELECT codigo_error, mensaje_error, contexto FROM log_errores WHERE id_log > :v_desde ORDER BY id_log;
exit
