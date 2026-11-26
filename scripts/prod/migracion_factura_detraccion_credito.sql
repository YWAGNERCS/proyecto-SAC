-- Ejecutar como propietario JDREFRIGERACION antes del despliegue.
-- Primero aplicar migracion_reglas_tributarias.sql si aun faltan esas columnas.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
  PROCEDURE agregar(tabla VARCHAR2, columna VARCHAR2, tipo VARCHAR2) IS
    n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO n FROM all_tab_columns WHERE owner=SYS_CONTEXT('USERENV','CURRENT_SCHEMA')
      AND table_name=tabla AND column_name=columna;
    IF n=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE '||tabla||' ADD ('||columna||' '||tipo||')'; END IF;
  END;
BEGIN
  agregar('VENTAS','MONTO_DETRACCION_SOLES','NUMBER(12,2)');
  agregar('COMPROBANTES','CUENTA_DETRACCIONES_EMISION','VARCHAR2(50)');
  agregar('COMPROBANTES','MEDIO_PAGO_DETRACCION','VARCHAR2(3)');
END;
/
