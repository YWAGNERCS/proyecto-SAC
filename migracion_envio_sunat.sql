-- ============================================================
-- Migración: respuesta de SUNAT por comprobante y estado OBSERVADO (envío directo a SUNAT)
-- En COMPROBANTES y NOTAS_CREDITO agrega el código y mensaje de SUNAT, el número de intentos,
-- la fecha del último intento, el XML firmado y el CDR (constancia de recepción). Además deja que
-- ESTADO_SUNAT acepte OBSERVADO (aceptado por SUNAT con observaciones).
--
-- Ejecutar UNA vez ANTES de arrancar el backend nuevo (si Hibernate agrega SUNAT_INTENTOS por su
-- cuenta en una tabla con filas, falla por ser NOT NULL sin valor):
--   Get-Content migracion_envio_sunat.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces: lo que ya existe no se toca.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    TYPE t_lista IS TABLE OF VARCHAR2(200);
    v_tablas   t_lista := t_lista('COMPROBANTES', 'NOTAS_CREDITO');
    v_columnas t_lista := t_lista(
        'SUNAT_CODIGO NUMBER(10)',
        'SUNAT_MENSAJE VARCHAR2(1000 CHAR)',
        'SUNAT_INTENTOS NUMBER(10) DEFAULT 0 NOT NULL',
        'SUNAT_ULTIMO_INTENTO TIMESTAMP(6)',
        'SUNAT_XML BLOB',
        'SUNAT_CDR BLOB');
    v_esquema  VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    v_existe   NUMBER;
    v_nombre   VARCHAR2(128);
BEGIN
    FOR t IN 1 .. v_tablas.COUNT LOOP
        FOR c IN 1 .. v_columnas.COUNT LOOP
            v_nombre := SUBSTR(v_columnas(c), 1, INSTR(v_columnas(c), ' ') - 1);
            SELECT COUNT(*) INTO v_existe FROM all_tab_columns
             WHERE owner = v_esquema AND table_name = v_tablas(t) AND column_name = v_nombre;
            IF v_existe = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE ' || v_tablas(t) || ' ADD (' || v_columnas(c) || ')';
                DBMS_OUTPUT.PUT_LINE(v_tablas(t) || '.' || v_nombre || ': agregada');
            END IF;
        END LOOP;

        -- Restricción del estado: la que generó Hibernate (sin OBSERVADO) se reemplaza
        FOR r IN (SELECT constraint_name, search_condition_vc FROM all_constraints
                   WHERE owner = v_esquema AND table_name = v_tablas(t) AND constraint_type = 'C'
                     AND UPPER(search_condition_vc) LIKE '%ESTADO_SUNAT IN%') LOOP
            IF INSTR(r.search_condition_vc, 'OBSERVADO') = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE ' || v_tablas(t) || ' DROP CONSTRAINT ' || r.constraint_name;
                DBMS_OUTPUT.PUT_LINE(v_tablas(t) || ': restricción ' || r.constraint_name || ' (sin OBSERVADO) eliminada');
            END IF;
        END LOOP;
        SELECT COUNT(*) INTO v_existe FROM all_constraints
         WHERE owner = v_esquema AND table_name = v_tablas(t) AND constraint_name = 'CK_' || SUBSTR(v_tablas(t), 1, 15) || '_ESTADO_SUNAT';
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || v_tablas(t) || ' ADD CONSTRAINT CK_' || SUBSTR(v_tablas(t), 1, 15)
                || '_ESTADO_SUNAT CHECK (estado_sunat IN (''PENDIENTE'', ''ACEPTADO'', ''OBSERVADO'', ''RECHAZADO''))';
            DBMS_OUTPUT.PUT_LINE(v_tablas(t) || ': ESTADO_SUNAT acepta OBSERVADO');
        END IF;
    END LOOP;
END;
/
EXIT
