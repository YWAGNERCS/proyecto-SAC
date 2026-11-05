-- ============================================================
-- Migración: una proforma se convierte en UNA sola venta (05/11/2026)
-- Índice único sobre VENTAS.PROFORMA_ID: si dos solicitudes simultáneas intentan convertir la misma
-- proforma, la base rechaza la segunda aunque pase las validaciones del servidor. Las ventas directas
-- (proforma_id nulo) no cuentan: Oracle no indexa filas con todas sus columnas nulas.
--
-- Antes de crearlo, el script avisa si ya hay proformas con más de una venta (habría que corregirlas a mano).
-- Ejecutar UNA vez, con el backend detenido o encendido:
--   Get-Content migracion_proforma_unica.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces: si el índice ya existe, no hace nada.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    v_duplicadas NUMBER;
    v_existe     NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_duplicadas FROM (
        SELECT proforma_id FROM ventas WHERE proforma_id IS NOT NULL GROUP BY proforma_id HAVING COUNT(*) > 1);
    IF v_duplicadas > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, v_duplicadas || ' proforma(s) ya tienen más de una venta: corríjalas antes de crear el índice.');
    END IF;

    SELECT COUNT(*) INTO v_existe FROM all_indexes
     WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND index_name = 'UX_VENTAS_PROFORMA';
    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_ventas_proforma ON ventas (proforma_id)';
        DBMS_OUTPUT.PUT_LINE('UX_VENTAS_PROFORMA: creado');
    ELSE
        DBMS_OUTPUT.PUT_LINE('UX_VENTAS_PROFORMA: ya existía');
    END IF;
END;
/
EXIT
