-- Ejecutar como propietario JDREFRIGERACION antes de desplegar el backend.
-- Idempotente. No reclasifica servicios ni modifica comprobantes historicos.
DECLARE
  PROCEDURE agregar(tabla VARCHAR2, columna VARCHAR2, definicion VARCHAR2) IS
    n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO n FROM all_tab_columns WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = tabla AND column_name = columna;
    IF n = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE ' || tabla || ' ADD (' || columna || ' ' || definicion || ')'; END IF;
  END;
BEGIN
  agregar('PRODUCTOS', 'CLASIFICACION_DETRACCION', 'VARCHAR2(30)');
  agregar('VENTAS', 'CODIGO_DETRACCION', 'VARCHAR2(3)');
  agregar('DETALLE_COBRO', 'BASE_RETENCION', 'NUMBER(12,2)');
  agregar('COBROS', 'TC_RETENCION', 'NUMBER(12,6)');
END;
/
