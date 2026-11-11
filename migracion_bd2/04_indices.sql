-- ============================================================
-- Migración BD2 · 4/7 · Índices medidos en BD2 S5 y S10, y el único que cierra la carrera del voucher (ADS S9)
--  * IX_DETALLE_VENTA_VENTA: FK sin índice; Hibernate la usa cada vez que carga las líneas de una venta.
--  * IX_VENTAS_FECHA_VENTA: ventas del día y del mes (rentabilidad).
--  * IX_VENTAS_CLIENTE_FECHA: cubre la FK VENTAS.CLIENTE_ID y las ventas de un cliente por fecha (S10).
--  * IX_COBROS_REFERENCIA_NORM: la expresión exacta de CobroRepository.vigentesConReferencia.
--  * UX_COBRO_VOUCHER_VIGENTE: único sobre (método, voucher normalizado) de los cobros VIGENTES. La consulta del
--    servicio da el mensaje claro; el índice garantiza la regla aunque dos cobros lleguen a la vez (ADS S9).
-- Lo ejecuta migracion_bd2\migrar.ps1 como SYSDBA. Se puede repetir.
-- ============================================================
whenever sqlerror exit failure
set serveroutput on feedback off
ALTER SESSION SET CONTAINER = FREEPDB1;
-- otra vez: DBMS_OUTPUT se habilita por contenedor
set serveroutput on
ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION;
DECLARE
    v_dup NUMBER;
    PROCEDURE ejecutar(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || SUBSTR(p_sql, 1, 110));
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN
            DBMS_OUTPUT.PUT_LINE('  ya existe: ' || SUBSTR(p_sql, 1, 100));
        ELSE
            RAISE;
        END IF;
    END;
BEGIN
    ejecutar('CREATE INDEX ix_detalle_venta_venta ON detalle_venta (venta_id) TABLESPACE ts_sgejd_ventas');
    ejecutar('CREATE INDEX ix_ventas_fecha_venta ON ventas (fecha_venta) TABLESPACE ts_sgejd_ventas');
    ejecutar('CREATE INDEX ix_ventas_cliente_fecha ON ventas (cliente_id, fecha_venta) TABLESPACE ts_sgejd_ventas');
    ejecutar('CREATE INDEX ix_cobros_referencia_norm ON cobros (UPPER(TRIM(referencia)))');

    -- Antes del índice único: si ya hay vouchers repetidos, se informa y NO se crea (habría que decidir cuál anular)
    SELECT COUNT(*) INTO v_dup FROM (
        SELECT 1 FROM cobros WHERE estado = 'VIGENTE' AND referencia IS NOT NULL
        GROUP BY metodo_pago, UPPER(TRIM(referencia)) HAVING COUNT(*) > 1);
    IF v_dup > 0 THEN
        DBMS_OUTPUT.PUT_LINE('  AVISO: ' || v_dup || ' vouchers vigentes repetidos; UX_COBRO_VOUCHER_VIGENTE no se crea');
    ELSE
        ejecutar('CREATE UNIQUE INDEX ux_cobro_voucher_vigente ON cobros (
            CASE WHEN estado = ''VIGENTE'' AND referencia IS NOT NULL THEN metodo_pago END,
            CASE WHEN estado = ''VIGENTE'' AND referencia IS NOT NULL THEN UPPER(TRIM(referencia)) END)');
    END IF;
END;
/
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('JDREFRIGERACION', cascade => TRUE)
set feedback on
column index_name format a28
column table_name format a16
SELECT table_name, index_name, uniqueness, status FROM dba_indexes
WHERE owner = 'JDREFRIGERACION' AND index_name IN ('IX_DETALLE_VENTA_VENTA', 'IX_VENTAS_FECHA_VENTA', 'IX_VENTAS_CLIENTE_FECHA',
      'IX_COBROS_REFERENCIA_NORM', 'UX_COBRO_VOUCHER_VIGENTE') ORDER BY table_name, index_name;
exit
