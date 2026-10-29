-- ============================================================
-- Migración: anulación de cobros y quién los registra (28/10/2026)
-- Un cobro mal registrado (monto o venta equivocados, pago duplicado, cheque rechazado)
-- ya no se borra: el administrador lo ANULA con un motivo y deja de contar en el saldo
-- de sus ventas. Además cada cobro guarda el usuario que lo registró, como las ventas.
--
-- Columnas nuevas en COBROS:
--   estado            VIGENTE | ANULADO (los cobros existentes quedan VIGENTE)
--   fecha_anulacion, motivo_anulacion, anulado_por   (obligatorios si está ANULADO)
--   registrado_por    usuario de Keycloak que registró el cobro (null en los anteriores)
--
-- Ejecutar UNA vez, con el backend detenido:
--   Get-Content migracion_cobros_anulacion.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces: si las columnas ya existen, no hace nada.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    v_existe NUMBER;

    PROCEDURE agregar(p_columna VARCHAR2, p_definicion VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_existe
          FROM all_tab_columns
         WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')
           AND table_name = 'COBROS' AND column_name = UPPER(p_columna);
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cobros ADD (' || p_columna || ' ' || p_definicion || ')';
            DBMS_OUTPUT.PUT_LINE('COBROS.' || p_columna || ': agregada');
        ELSE
            DBMS_OUTPUT.PUT_LINE('COBROS.' || p_columna || ': ya existe');
        END IF;
    END;
BEGIN
    agregar('estado', q'[VARCHAR2(10) DEFAULT 'VIGENTE' NOT NULL CONSTRAINT ck_cobro_estado CHECK (estado IN ('VIGENTE', 'ANULADO'))]');
    agregar('fecha_anulacion', 'DATE');
    agregar('motivo_anulacion', 'VARCHAR2(300)');
    agregar('anulado_por', 'VARCHAR2(100)');
    agregar('registrado_por', 'VARCHAR2(100)');

    SELECT COUNT(*) INTO v_existe
      FROM all_constraints
     WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND constraint_name = 'CK_COBRO_ANULADO';
    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE cobros ADD CONSTRAINT ck_cobro_anulado CHECK (
            estado = 'VIGENTE' OR (fecha_anulacion IS NOT NULL AND motivo_anulacion IS NOT NULL AND anulado_por IS NOT NULL))]';
        DBMS_OUTPUT.PUT_LINE('CK_COBRO_ANULADO: creada');
    END IF;
END;
/
