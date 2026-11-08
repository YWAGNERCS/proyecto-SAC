-- ============================================================
-- Migración: ambiente de emisión de cada comprobante, inmutable (08/11/2026)
--
-- COMPROBANTES.AMBIENTE_EMISION: en qué ambiente de SUNAT se aceptó cada factura o boleta (PRODUCCION, BETA,
--   SIMULADO). SGE-JD lo fija al aceptarse y ya no cambia; el correo al cliente solo sale si es PRODUCCION, aunque
--   después se cambie la configuración del servidor (de beta a producción o al revés).
-- TRG_COMPROBANTE_AMBIENTE: impide cambiar o borrar el ambiente una vez registrado (también desde SQL).
-- ENVIOS_SUNAT.AMBIENTE: el ambiente de cada envío a SUNAT, para la auditoría (null en los anteriores).
--
-- Los comprobantes ya aceptados antes de esta migración:
--   - sin XML firmado → SIMULADO (nunca salieron a SUNAT);
--   - con XML firmado → NO_REGISTRADO: no quedó guardado a qué ambiente se enviaron, así que NO se tratan como válidos
--     para enviarlos por correo (hasta el 08/11/2026 SGE-JD solo se usó con la beta de SUNAT o en modo simulado).
--
-- Solo agrega: no borra datos. Se puede ejecutar varias veces.
-- Uso:
--   docker cp migracion_ambiente_emision.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
-- ============================================================
SET SERVEROUTPUT ON
DECLARE
    v_esquema VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    v_existe  NUMBER;

    PROCEDURE agregar(p_tabla VARCHAR2, p_columna VARCHAR2, p_tipo VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM all_tab_columns
         WHERE owner = v_esquema AND table_name = p_tabla AND column_name = p_columna;
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || v_esquema || '.' || p_tabla || ' ADD (' || p_columna || ' ' || p_tipo || ')';
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': agregada');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': ya existía');
        END IF;
    END;
BEGIN
    agregar('COMPROBANTES', 'AMBIENTE_EMISION', 'VARCHAR2(13 CHAR) CONSTRAINT CK_COMPROBANTE_AMBIENTE CHECK '
        || '(AMBIENTE_EMISION IN (''PRODUCCION'', ''BETA'', ''SIMULADO'', ''NO_REGISTRADO''))');
    agregar('ENVIOS_SUNAT', 'AMBIENTE', 'VARCHAR2(13 CHAR)');

    -- Los ya aceptados (antes de que el ambiente se registrara)
    EXECUTE IMMEDIATE '
        UPDATE ' || v_esquema || '.COMPROBANTES
           SET AMBIENTE_EMISION = CASE WHEN SUNAT_XML IS NULL THEN ''SIMULADO'' ELSE ''NO_REGISTRADO'' END
         WHERE AMBIENTE_EMISION IS NULL AND ESTADO_SUNAT IN (''ACEPTADO'', ''OBSERVADO'')';
    DBMS_OUTPUT.PUT_LINE('COMPROBANTES: ambiente de los ya aceptados en ' || SQL%ROWCOUNT || ' comprobante(s)');
    COMMIT;

    -- Una vez registrado, el ambiente no se cambia ni se borra
    EXECUTE IMMEDIATE '
        CREATE OR REPLACE TRIGGER ' || v_esquema || '.TRG_COMPROBANTE_AMBIENTE
        BEFORE UPDATE OF AMBIENTE_EMISION ON ' || v_esquema || '.COMPROBANTES
        FOR EACH ROW
        BEGIN
            IF :OLD.AMBIENTE_EMISION IS NOT NULL
               AND (:NEW.AMBIENTE_EMISION IS NULL OR :NEW.AMBIENTE_EMISION <> :OLD.AMBIENTE_EMISION) THEN
                RAISE_APPLICATION_ERROR(-20031, ''El ambiente de emision de un comprobante no se puede cambiar (''
                    || :OLD.AMBIENTE_EMISION || '').'');
            END IF;
        END;';
    DBMS_OUTPUT.PUT_LINE('TRG_COMPROBANTE_AMBIENTE: creado o actualizado');
END;
/
