-- ============================================================
-- BD2 · S9 · Paso 3: auditoría unificada. Como SYSTEM en FREEPDB1:
--   .\bd2\herramientas\sql.ps1 bd2\S09\S09_03_auditoria.sql -Dba
-- Se puede repetir (desactiva y borra las políticas antes de crearlas).
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue
column policy_name format a24
column entity_name format a16
column object_name format a16
column audit_option format a10

NOAUDIT POLICY pol_sgejd_ventas;
NOAUDIT POLICY pol_sgejd_sesiones BY jd_app, jd_reportes;
DROP AUDIT POLICY pol_sgejd_ventas;
DROP AUDIT POLICY pol_sgejd_sesiones;

-- La sintaxis tradicional ya no se admite (se prueba a propósito)
AUDIT INSERT, UPDATE, DELETE ON jdrefrigeracion.ventas BY ACCESS;

-- 1) Sentencias que cambian dinero: ventas, sus líneas y los cobros (de cualquier usuario)
-- Cada acción lleva su propio ON. "INSERT, UPDATE, DELETE ON tabla" (como en la guía) audita INSERT y UPDATE
-- sobre CUALQUIER objeto de la base y solo DELETE sobre la tabla: AUDIT_UNIFIED_POLICIES lo muestra con
-- OBJECT_NAME = NONE (hallazgo de S9, primera corrida).
CREATE AUDIT POLICY pol_sgejd_ventas
  ACTIONS INSERT ON jdrefrigeracion.ventas,        UPDATE ON jdrefrigeracion.ventas,        DELETE ON jdrefrigeracion.ventas,
          INSERT ON jdrefrigeracion.detalle_venta, UPDATE ON jdrefrigeracion.detalle_venta, DELETE ON jdrefrigeracion.detalle_venta,
          INSERT ON jdrefrigeracion.cobros,        UPDATE ON jdrefrigeracion.cobros,        DELETE ON jdrefrigeracion.cobros;
AUDIT POLICY pol_sgejd_ventas;

-- 2) Conexiones de los usuarios de la aplicación, y todo intento fallido de conexión (de cualquiera)
CREATE AUDIT POLICY pol_sgejd_sesiones ACTIONS LOGON, LOGOFF;
AUDIT POLICY pol_sgejd_sesiones BY jd_app, jd_reportes;
AUDIT POLICY ora_logon_failures;

SELECT policy_name, enabled_option, entity_name, success, failure FROM audit_unified_enabled_policies
WHERE policy_name IN ('POL_SGEJD_VENTAS', 'POL_SGEJD_SESIONES', 'ORA_LOGON_FAILURES') ORDER BY policy_name, entity_name;
SELECT policy_name, audit_option, object_name FROM audit_unified_policies
WHERE policy_name = 'POL_SGEJD_VENTAS' ORDER BY object_name, audit_option;
exit
