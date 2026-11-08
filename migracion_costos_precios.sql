-- ============================================================
-- Migración: IGV de las compras, último costo de compra, precio editado y topes de descuento
-- (auditoría M1 y M2, 08/11/2026)
--
-- COMPRAS gana SUBTOTAL e IGV: Lisandro escribe los precios de compra tal como los pagó (IGV incluido), así que
--   en las compras ya registradas el total se separa igual: subtotal = total / 1.18 e IGV = total - subtotal.
-- PRODUCTOS gana ULTIMO_COSTO_COMPRA (sin IGV, en la moneda del producto) y FECHA_ULTIMA_COMPRA: se completan con
--   la compra más reciente de cada producto en su misma moneda (las de otra moneda no guardan tipo de cambio).
--   No cambia ningún precio: sirve para el aviso en Ventas y para la rentabilidad de las ventas nuevas.
-- DETALLE_VENTA e ITEMS_PROFORMA ganan PRECIO_CATALOGO, PRECIO_EDITADO_POR y MOTIVO_PRECIO: el precio de catálogo,
--   quién lo editó y por qué cuando se cambia al registrar (las filas anteriores quedan vacías = precio de catálogo).
-- TOPES_DESCUENTO (nueva): los topes que Lisandro fija en Configuración. Vacía = los de application.properties.
--
-- Uso (idempotente: lo que ya existe no se toca):
--   docker cp migracion_costos_precios.sql SGE-JD:/tmp/m.sql
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
    agregar('COMPRAS', 'SUBTOTAL', 'NUMBER(12,2)');
    agregar('COMPRAS', 'IGV', 'NUMBER(12,2)');
    agregar('PRODUCTOS', 'ULTIMO_COSTO_COMPRA', 'NUMBER(12,2)');
    agregar('PRODUCTOS', 'FECHA_ULTIMA_COMPRA', 'DATE');
    agregar('DETALLE_VENTA', 'PRECIO_CATALOGO', 'NUMBER(12,2)');
    agregar('DETALLE_VENTA', 'PRECIO_EDITADO_POR', 'VARCHAR2(100 CHAR)');
    agregar('DETALLE_VENTA', 'MOTIVO_PRECIO', 'VARCHAR2(200 CHAR)');
    agregar('ITEMS_PROFORMA', 'PRECIO_CATALOGO', 'NUMBER(12,2)');
    agregar('ITEMS_PROFORMA', 'PRECIO_EDITADO_POR', 'VARCHAR2(100 CHAR)');
    agregar('ITEMS_PROFORMA', 'MOTIVO_PRECIO', 'VARCHAR2(200 CHAR)');

    SELECT COUNT(*) INTO v_existe FROM all_tables WHERE owner = v_esquema AND table_name = 'TOPES_DESCUENTO';
    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE ' || v_esquema || '.TOPES_DESCUENTO (
            ID NUMBER(19,0) NOT NULL,
            TOPE_ADMIN NUMBER(5,2) NOT NULL,
            TOPE_ASISTENTE NUMBER(5,2) NOT NULL,
            ACTUALIZADO_POR VARCHAR2(100 CHAR),
            FECHA_ACTUALIZACION TIMESTAMP(6),
            CONSTRAINT PK_TOPES_DESCUENTO PRIMARY KEY (ID),
            CONSTRAINT CK_TOPES_DESCUENTO_UNICA CHECK (ID = 1),
            CONSTRAINT CK_TOPES_DESCUENTO_RANGO CHECK (TOPE_ADMIN BETWEEN 0 AND 100 AND TOPE_ASISTENTE BETWEEN 0 AND TOPE_ADMIN))';
        DBMS_OUTPUT.PUT_LINE('TOPES_DESCUENTO: creada');
    ELSE
        DBMS_OUTPUT.PUT_LINE('TOPES_DESCUENTO: ya existía');
    END IF;

    -- Compras ya registradas: el total se escribió con IGV incluido
    EXECUTE IMMEDIATE '
        UPDATE ' || v_esquema || '.COMPRAS
           SET SUBTOTAL = ROUND(TOTAL / 1.18, 2), IGV = TOTAL - ROUND(TOTAL / 1.18, 2)
         WHERE SUBTOTAL IS NULL OR IGV IS NULL';
    DBMS_OUTPUT.PUT_LINE('COMPRAS: subtotal e IGV en ' || SQL%ROWCOUNT || ' compra(s)');

    -- Último costo de compra: la línea más reciente de cada producto en su misma moneda, sin IGV
    EXECUTE IMMEDIATE '
        MERGE INTO ' || v_esquema || '.PRODUCTOS p
        USING (SELECT producto_id, costo, fecha FROM (
                   SELECT d.PRODUCTO_ID producto_id, ROUND(d.PRECIO_UNITARIO_COMPRA / 1.18, 2) costo,
                          c.FECHA_COMPRA fecha,
                          ROW_NUMBER() OVER (PARTITION BY d.PRODUCTO_ID ORDER BY c.FECHA_COMPRA DESC, c.ID DESC, d.ID DESC) n
                     FROM ' || v_esquema || '.DETALLE_COMPRA d
                     JOIN ' || v_esquema || '.COMPRAS c ON c.ID = d.COMPRA_ID
                     JOIN ' || v_esquema || '.PRODUCTOS pr ON pr.ID = d.PRODUCTO_ID AND pr.MONEDA = c.MONEDA)
                WHERE n = 1) u
           ON (p.ID = u.producto_id)
         WHEN MATCHED THEN UPDATE SET p.ULTIMO_COSTO_COMPRA = u.costo, p.FECHA_ULTIMA_COMPRA = u.fecha
                          WHERE p.ULTIMO_COSTO_COMPRA IS NULL';
    DBMS_OUTPUT.PUT_LINE('PRODUCTOS: último costo de compra en ' || SQL%ROWCOUNT || ' producto(s)');
    COMMIT;
END;
/
