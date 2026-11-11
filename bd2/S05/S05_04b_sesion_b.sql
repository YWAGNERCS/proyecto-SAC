-- BD2 · S5 · Sesión B del paso 4: al mismo tiempo, otra venta genera otra fila de auditoría con la misma ACCION.
set feedback off serveroutput on
DECLARE
    v_ini TIMESTAMP := SYSTIMESTAMP;
    v_indice VARCHAR2(30);
BEGIN
    SELECT index_type INTO v_indice FROM user_indexes WHERE index_name LIKE '%VENTA_AUDITORIA_ACCION';
    DBMS_OUTPUT.PUT_LINE('Sesion B: índice sobre ACCION de tipo ' || v_indice || '; inserta a las ' || TO_CHAR(v_ini, 'HH24:MI:SS'));
    INSERT INTO venta_auditoria (id_venta, accion, estado_nuevo) VALUES (-2, 'UPDATE', 'PRUEBA S5');
    DBMS_OUTPUT.PUT_LINE('Sesion B: su INSERT terminó a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS')
        || ' (esperó ' || ROUND(EXTRACT(SECOND FROM (SYSTIMESTAMP - v_ini)), 1) || ' s)');
    ROLLBACK;
END;
/
exit
