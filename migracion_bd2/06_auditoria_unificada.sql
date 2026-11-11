-- ============================================================
-- Migración BD2 · 6/7 · Auditoría unificada (BD2 S9)
--  * POL_SGEJD_VENTAS: INSERT, UPDATE y DELETE sobre ventas, sus líneas y los cobros, de cualquier usuario.
--    Cada acción lleva su propio ON: con "INSERT, UPDATE, DELETE ON tabla" Oracle audita INSERT y UPDATE sobre
--    CUALQUIER objeto (hallazgo de S9).
--  * POL_SGEJD_SESIONES: conexiones de JD_APP y JD_REPORTES.
--  * ORA_LOGON_FAILURES: toda clave incorrecta (viene con Oracle; se activa).
-- Lo ejecuta migracion_bd2\migrar.ps1 como SYSDBA. Se puede repetir.
-- ============================================================
whenever sqlerror exit failure
set serveroutput on feedback off
ALTER SESSION SET CONTAINER = FREEPDB1;
-- otra vez: DBMS_OUTPUT se habilita por contenedor
set serveroutput on
DECLARE
    v_n NUMBER;
    PROCEDURE ejecutar(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || SUBSTR(p_sql, 1, 110));
    END;
BEGIN
    SELECT COUNT(*) INTO v_n FROM audit_unified_policies WHERE policy_name = 'POL_SGEJD_VENTAS';
    IF v_n = 0 THEN
        ejecutar('CREATE AUDIT POLICY pol_sgejd_ventas ACTIONS
            INSERT ON jdrefrigeracion.ventas,        UPDATE ON jdrefrigeracion.ventas,        DELETE ON jdrefrigeracion.ventas,
            INSERT ON jdrefrigeracion.detalle_venta, UPDATE ON jdrefrigeracion.detalle_venta, DELETE ON jdrefrigeracion.detalle_venta,
            INSERT ON jdrefrigeracion.cobros,        UPDATE ON jdrefrigeracion.cobros,        DELETE ON jdrefrigeracion.cobros');
    END IF;
    SELECT COUNT(*) INTO v_n FROM audit_unified_policies WHERE policy_name = 'POL_SGEJD_SESIONES';
    IF v_n = 0 THEN
        ejecutar('CREATE AUDIT POLICY pol_sgejd_sesiones ACTIONS LOGON, LOGOFF');
    END IF;
    SELECT COUNT(*) INTO v_n FROM audit_unified_enabled_policies WHERE policy_name = 'POL_SGEJD_VENTAS';
    IF v_n = 0 THEN ejecutar('AUDIT POLICY pol_sgejd_ventas'); END IF;
    SELECT COUNT(*) INTO v_n FROM audit_unified_enabled_policies WHERE policy_name = 'POL_SGEJD_SESIONES';
    IF v_n = 0 THEN ejecutar('AUDIT POLICY pol_sgejd_sesiones BY jd_app, jd_reportes'); END IF;
    SELECT COUNT(*) INTO v_n FROM audit_unified_enabled_policies WHERE policy_name = 'ORA_LOGON_FAILURES';
    IF v_n = 0 THEN ejecutar('AUDIT POLICY ora_logon_failures'); END IF;
END;
/
set feedback on
column policy_name format a22
column entity_name format a14
SELECT policy_name, enabled_option, entity_name FROM audit_unified_enabled_policies
WHERE policy_name IN ('POL_SGEJD_VENTAS', 'POL_SGEJD_SESIONES', 'ORA_LOGON_FAILURES') ORDER BY policy_name, entity_name;
SELECT COUNT(*) AS acciones_auditadas, COUNT(DISTINCT object_name) AS tablas
FROM audit_unified_policies WHERE policy_name = 'POL_SGEJD_VENTAS';
exit
