-- Una factura de proveedor se registra una sola vez (revisión externa, 07/11/2026).
-- CompraService ya lo valida con una consulta, pero dos compras simultáneas con la misma factura podían
-- pasar las dos esa consulta antes de guardarse. El índice único hace que la segunda falle en Oracle (409).
-- El N° de factura es obligatorio y se guarda normalizado (F001-00000123), así que la comparación es exacta.
--
-- Uso (idempotente: si el índice ya existe, no hace nada):
--   docker cp migracion_compra_factura_unica.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
SET FEEDBACK ON
SET SERVEROUTPUT ON
DECLARE
    v_duplicadas NUMBER;
    v_existe     NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_duplicadas FROM (
        SELECT proveedor_id, numero_factura FROM compras GROUP BY proveedor_id, numero_factura HAVING COUNT(*) > 1);
    IF v_duplicadas > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, v_duplicadas || ' factura(s) de proveedor están registradas más de una vez: corríjalas antes de crear el índice.');
    END IF;
    SELECT COUNT(*) INTO v_existe FROM all_indexes
     WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND index_name = 'UX_COMPRAS_PROVEEDOR_FACTURA';
    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_compras_proveedor_factura ON compras (proveedor_id, numero_factura)';
        DBMS_OUTPUT.PUT_LINE('UX_COMPRAS_PROVEEDOR_FACTURA: creado');
    ELSE
        DBMS_OUTPUT.PUT_LINE('UX_COMPRAS_PROVEEDOR_FACTURA: ya existía');
    END IF;
END;
/
EXIT
