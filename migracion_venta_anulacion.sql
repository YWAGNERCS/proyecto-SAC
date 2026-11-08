-- ============================================================
-- Migración: quién anuló una venta y por qué (auditoría M4, 07/11/2026)
--
-- VENTAS gana ANULADO_POR (usuario del token) y MOTIVO_ANULACION (obligatorio al anular, como en Cobranza).
-- Para las ventas ya anuladas, ANULADO_POR se completa con el historial de cambios (la anulación exitosa
-- de esa venta, PATCH /api/v1/admin/ventas/{id}/anular) y el motivo queda como "Anulada antes de pedir el
-- motivo", porque antes no se pedía. Las anuladas antes de que existiera el historial quedan sin usuario.
--
-- Uso (idempotente: lo que ya existe no se toca):
--   docker cp migracion_venta_anulacion.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
-- ============================================================
SET SERVEROUTPUT ON
DECLARE
    v_esquema VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    v_existe  NUMBER;

    PROCEDURE agregar(p_columna VARCHAR2, p_tipo VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM all_tab_columns
         WHERE owner = v_esquema AND table_name = 'VENTAS' AND column_name = p_columna;
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || v_esquema || '.VENTAS ADD (' || p_columna || ' ' || p_tipo || ')';
            DBMS_OUTPUT.PUT_LINE('VENTAS.' || p_columna || ': agregada');
        ELSE
            DBMS_OUTPUT.PUT_LINE('VENTAS.' || p_columna || ': ya existía');
        END IF;
    END;
BEGIN
    agregar('ANULADO_POR', 'VARCHAR2(100 CHAR)');
    agregar('MOTIVO_ANULACION', 'VARCHAR2(300 CHAR)');

    -- Quién anuló cada venta ya anulada, según el historial de cambios (la anulación exitosa más reciente)
    EXECUTE IMMEDIATE '
        UPDATE ' || v_esquema || '.VENTAS v
           SET v.ANULADO_POR = (SELECT MAX(h.USUARIO) KEEP (DENSE_RANK LAST ORDER BY h.FECHA_HORA)
                                  FROM ' || v_esquema || '.HISTORIAL_CAMBIOS h
                                 WHERE h.METODO = ''PATCH'' AND h.RESULTADO = ''EXITO''
                                   AND h.RUTA = ''/api/v1/admin/ventas/'' || v.ID || ''/anular'')
         WHERE v.ESTADO = ''ANULADA'' AND v.ANULADO_POR IS NULL
           AND EXISTS (SELECT 1 FROM ' || v_esquema || '.HISTORIAL_CAMBIOS h
                        WHERE h.METODO = ''PATCH'' AND h.RESULTADO = ''EXITO''
                          AND h.RUTA = ''/api/v1/admin/ventas/'' || v.ID || ''/anular'')';
    DBMS_OUTPUT.PUT_LINE('VENTAS: quién anuló, completado desde el historial en ' || SQL%ROWCOUNT || ' venta(s) anulada(s)');

    EXECUTE IMMEDIATE '
        UPDATE ' || v_esquema || '.VENTAS
           SET MOTIVO_ANULACION = ''Anulada antes de pedir el motivo (07/11/2026)''
         WHERE ESTADO = ''ANULADA'' AND MOTIVO_ANULACION IS NULL';
    DBMS_OUTPUT.PUT_LINE('VENTAS: motivo de las anuladas antes del cambio, en ' || SQL%ROWCOUNT || ' venta(s)');
    COMMIT;
END;
/
