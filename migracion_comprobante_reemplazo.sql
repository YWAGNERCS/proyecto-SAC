-- ============================================================
-- Migración: comprobante de reemplazo, historial de envíos a SUNAT y datos del receptor fijos
-- (informe de rechazos SUNAT, puntos D-2, D-3, D-4 y D-5; 07/11/2026)
--
-- 1. COMPROBANTES puede tener más de un comprobante por venta: cuando SUNAT rechaza uno con CDR, su número
--    queda usado y se emite otro con número nuevo para la MISMA venta. El rechazado se marca REEMPLAZADO y
--    el nuevo apunta a él (REEMPLAZA_A_ID). Sale el índice único de VENTA_ID y entra uno que permite un solo
--    comprobante NO reemplazado por venta.
-- 2. COMPROBANTES guarda los datos del receptor al emitir (tipo y número de documento, nombre, dirección):
--    editar el cliente después ya no cambia el XML ni el PDF de un comprobante emitido.
-- 3. DETALLE_VENTA guarda la descripción del producto al vender, por la misma razón.
-- 4. ENVIOS_SUNAT guarda cada respuesta de SUNAT (código, mensaje, XML y CDR) sin pisar las anteriores:
--    SUNAT exige conservar las constancias de rechazo.
--
-- Los comprobantes ya emitidos toman el receptor de los datos ACTUALES del cliente (no hay otra fuente
-- en la base); el XML que SUNAT recibió sigue guardado tal cual en SUNAT_XML.
--
-- Uso (idempotente: lo que ya existe no se toca):
--   docker cp migracion_comprobante_reemplazo.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    v_esquema VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    n NUMBER;

    FUNCTION existe_columna(p_tabla VARCHAR2, p_columna VARCHAR2) RETURN BOOLEAN IS
        c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO c FROM all_tab_columns WHERE owner = v_esquema AND table_name = p_tabla AND column_name = p_columna;
        RETURN c > 0;
    END;

    PROCEDURE agregar(p_tabla VARCHAR2, p_columna VARCHAR2, p_tipo VARCHAR2) IS
    BEGIN
        IF NOT existe_columna(p_tabla, p_columna) THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' ADD ' || p_columna || ' ' || p_tipo;
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': agregada');
        END IF;
    END;
BEGIN
    -- 1. Datos del receptor y reemplazo en COMPROBANTES
    agregar('COMPROBANTES', 'RECEPTOR_TIPO_DOC', 'VARCHAR2(1 CHAR)');
    agregar('COMPROBANTES', 'RECEPTOR_NUM_DOC', 'VARCHAR2(15 CHAR)');
    agregar('COMPROBANTES', 'RECEPTOR_NOMBRE', 'VARCHAR2(150 CHAR)');
    agregar('COMPROBANTES', 'RECEPTOR_DIRECCION', 'VARCHAR2(200 CHAR)');
    agregar('COMPROBANTES', 'REEMPLAZADO', 'BOOLEAN DEFAULT FALSE NOT NULL');
    agregar('COMPROBANTES', 'REEMPLAZA_A_ID', 'NUMBER(19,0)');

    -- Comprobantes ya emitidos: el receptor sale de los datos actuales del cliente
    EXECUTE IMMEDIATE q'[
        UPDATE comprobantes c SET
            (receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion) = (
                SELECT CASE WHEN cl.ruc IS NOT NULL THEN '6' WHEN cl.dni IS NOT NULL THEN '1' ELSE '0' END,
                       COALESCE(cl.ruc, cl.dni, '-'), cl.nombreorazon_social, cl.direccion
                  FROM ventas v JOIN clientes cl ON cl.id = v.cliente_id
                 WHERE v.id = c.venta_id)
         WHERE c.receptor_num_doc IS NULL]';
    DBMS_OUTPUT.PUT_LINE('COMPROBANTES: receptor completado en ' || SQL%ROWCOUNT || ' fila(s)');

    SELECT COUNT(*) INTO n FROM all_constraints WHERE owner = v_esquema AND table_name = 'COMPROBANTES' AND constraint_name = 'FK_COMPROBANTE_REEMPLAZA_A';
    IF n = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE comprobantes ADD CONSTRAINT fk_comprobante_reemplaza_a FOREIGN KEY (reemplaza_a_id) REFERENCES comprobantes (id)';
        DBMS_OUTPUT.PUT_LINE('FK_COMPROBANTE_REEMPLAZA_A: creada');
    END IF;

    -- Sale el único de VENTA_ID (Hibernate le dio un nombre generado: se busca por su columna)
    FOR r IN (SELECT c.constraint_name FROM all_constraints c
               WHERE c.owner = v_esquema AND c.table_name = 'COMPROBANTES' AND c.constraint_type = 'U'
                 AND (SELECT LISTAGG(cc.column_name, ',') WITHIN GROUP (ORDER BY cc.position)
                        FROM all_cons_columns cc WHERE cc.owner = c.owner AND cc.constraint_name = c.constraint_name) = 'VENTA_ID') LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE comprobantes DROP CONSTRAINT ' || r.constraint_name || ' DROP INDEX';
        DBMS_OUTPUT.PUT_LINE(r.constraint_name || ' (único de VENTA_ID): eliminado');
    END LOOP;

    -- Entra: un solo comprobante no reemplazado por venta
    SELECT COUNT(*) INTO n FROM all_indexes WHERE owner = v_esquema AND index_name = 'UX_COMPROBANTE_VIGENTE_VENTA';
    IF n = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_comprobante_vigente_venta ON comprobantes (CASE WHEN reemplazado = FALSE THEN venta_id END)';
        DBMS_OUTPUT.PUT_LINE('UX_COMPROBANTE_VIGENTE_VENTA: creado');
    END IF;
    -- VENTA_ID sigue necesitando un índice propio para buscar los comprobantes de una venta
    SELECT COUNT(*) INTO n FROM all_indexes WHERE owner = v_esquema AND index_name = 'IX_COMPROBANTES_VENTA';
    IF n = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_comprobantes_venta ON comprobantes (venta_id)';
        DBMS_OUTPUT.PUT_LINE('IX_COMPROBANTES_VENTA: creado');
    END IF;

    -- 2. Descripción de cada línea al vender
    agregar('DETALLE_VENTA', 'DESCRIPCION', 'VARCHAR2(150 CHAR)');
    EXECUTE IMMEDIATE q'[
        UPDATE detalle_venta d SET descripcion = (SELECT p.nombre FROM productos p WHERE p.id = d.producto_id)
         WHERE d.descripcion IS NULL]';
    DBMS_OUTPUT.PUT_LINE('DETALLE_VENTA: descripción completada en ' || SQL%ROWCOUNT || ' fila(s)');

    -- 3. Historial de envíos a SUNAT
    SELECT COUNT(*) INTO n FROM all_tables WHERE owner = v_esquema AND table_name = 'ENVIOS_SUNAT';
    IF n = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE envios_sunat (
                id              NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                comprobante_id  NUMBER(19,0) REFERENCES comprobantes (id),
                nota_credito_id NUMBER(19,0) REFERENCES notas_credito (id),
                intento         NUMBER(10,0) NOT NULL,
                fecha           TIMESTAMP(6) NOT NULL,
                resultado       VARCHAR2(15 CHAR) NOT NULL,
                codigo          NUMBER(10,0),
                mensaje         VARCHAR2(1000 CHAR),
                xml             BLOB,
                cdr             BLOB,
                CONSTRAINT ck_envios_sunat_documento CHECK ((comprobante_id IS NULL) <> (nota_credito_id IS NULL))
            )]';
        EXECUTE IMMEDIATE 'CREATE INDEX ix_envios_sunat_comprobante ON envios_sunat (comprobante_id)';
        EXECUTE IMMEDIATE 'CREATE INDEX ix_envios_sunat_nota ON envios_sunat (nota_credito_id)';
        DBMS_OUTPUT.PUT_LINE('ENVIOS_SUNAT: creada');

        -- El último envío de cada documento ya enviado pasa al historial (los anteriores no se guardaron)
        EXECUTE IMMEDIATE q'[
            INSERT INTO envios_sunat (comprobante_id, intento, fecha, resultado, codigo, mensaje, xml, cdr)
            SELECT id, sunat_intentos, sunat_ultimo_intento, estado_sunat, sunat_codigo, sunat_mensaje, sunat_xml, sunat_cdr
              FROM comprobantes WHERE sunat_intentos > 0 AND sunat_ultimo_intento IS NOT NULL]';
        DBMS_OUTPUT.PUT_LINE('ENVIOS_SUNAT: ' || SQL%ROWCOUNT || ' envío(s) de comprobantes copiados');
        EXECUTE IMMEDIATE q'[
            INSERT INTO envios_sunat (nota_credito_id, intento, fecha, resultado, codigo, mensaje, xml, cdr)
            SELECT id, sunat_intentos, sunat_ultimo_intento, estado_sunat, sunat_codigo, sunat_mensaje, sunat_xml, sunat_cdr
              FROM notas_credito WHERE sunat_intentos > 0 AND sunat_ultimo_intento IS NOT NULL]';
        DBMS_OUTPUT.PUT_LINE('ENVIOS_SUNAT: ' || SQL%ROWCOUNT || ' envío(s) de notas copiados');
    END IF;

    COMMIT;
END;
/
EXIT
