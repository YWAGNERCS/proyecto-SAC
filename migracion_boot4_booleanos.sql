-- ============================================================
-- Migración: booleanos a BOOLEAN nativo de Oracle 23 (Spring Boot 4 / Hibernate 7, 22/10/2026)
-- Hibernate 6 creó los boolean como NUMBER(1) con CHECK (x in (0,1)). Hibernate 7, en Oracle 23,
-- los mapea al tipo BOOLEAN nativo y al arrancar intenta convertirlos, pero Oracle lo rechaza
-- (ORA-01463 por el CHECK; ORA-01439 si la columna tiene datos). Este script hace la conversión
-- conservando los valores: columna nueva BOOLEAN, copia, borra la vieja y renombra.
--
-- Columnas: PRODUCTOS.visible_en_catalogo, MANTENIMIENTOS.alerta_enviada,
--           PROFORMAS.enviado_correo, PROFORMAS.enviado_whatsapp
--
-- Ejecutar UNA vez, con el backend detenido:
--   Get-Content migracion_boot4_booleanos.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces sin efectos duplicados (salta las columnas que ya son BOOLEAN).
-- Los INSERT de datos_demo.sql siguen funcionando: Oracle acepta 1/0 en una columna BOOLEAN.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    PROCEDURE convertir(p_tabla VARCHAR2, p_columna VARCHAR2) IS
        v_tipo     VARCHAR2(128);
        v_nullable VARCHAR2(1);
    BEGIN
        SELECT data_type, nullable INTO v_tipo, v_nullable
          FROM user_tab_columns
         WHERE table_name = p_tabla AND column_name = p_columna;

        IF v_tipo = 'BOOLEAN' THEN
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': ya es BOOLEAN');
            RETURN;
        END IF;

        -- El CHECK (columna in (0,1)) se va junto con la columna vieja
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' ADD (' || p_columna || '_B BOOLEAN)';
        EXECUTE IMMEDIATE 'UPDATE ' || p_tabla || ' SET ' || p_columna || '_B = (' || p_columna || ' = 1)';
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' DROP COLUMN ' || p_columna;
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' RENAME COLUMN ' || p_columna || '_B TO ' || p_columna;
        IF v_nullable = 'N' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' MODIFY ' || p_columna || ' NOT NULL';
        END IF;
        DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': NUMBER(1) -> BOOLEAN');
    END;
BEGIN
    convertir('PRODUCTOS', 'VISIBLE_EN_CATALOGO');
    convertir('MANTENIMIENTOS', 'ALERTA_ENVIADA');
    convertir('PROFORMAS', 'ENVIADO_CORREO');
    convertir('PROFORMAS', 'ENVIADO_WHATSAPP');
END;
/

-- Verificación: las 4 columnas deben decir BOOLEAN
COLUMN table_name  FORMAT A16
COLUMN column_name FORMAT A22
COLUMN data_type   FORMAT A10
SELECT table_name, column_name, data_type, nullable
  FROM user_tab_columns
 WHERE column_name IN ('VISIBLE_EN_CATALOGO', 'ALERTA_ENVIADA', 'ENVIADO_CORREO', 'ENVIADO_WHATSAPP')
 ORDER BY table_name, column_name;
