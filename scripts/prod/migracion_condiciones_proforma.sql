-- Ejecutar con el propietario del esquema antes de desplegar el backend.
-- Las proformas anteriores conservan condiciones vacías. Se puede repetir.
DECLARE
    columnas NUMBER;
BEGIN
    SELECT COUNT(*) INTO columnas FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'PROFORMAS' AND COLUMN_NAME = 'CONDICIONES_PAGO';
    IF columnas = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PROFORMAS ADD (CONDICIONES_PAGO VARCHAR2(1000 CHAR))';
    END IF;
END;
/
