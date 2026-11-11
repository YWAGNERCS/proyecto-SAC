-- ============================================================
-- BD2 · S9 · Paso 4: lo que hace JD_APP con la auditoría encendida. Como JD_APP:
--   .\bd2\herramientas\sql.ps1 bd2\S09\S09_04_evidencia_jd_app.sql -Usuario JD_APP
-- ============================================================
set echo on feedback on linesize 200
whenever sqlerror continue
-- Algo permitido que cambia dinero (queda auditado aunque se deshaga)
UPDATE jdrefrigeracion.ventas SET estado_pago = estado_pago WHERE id = (SELECT MIN(id) FROM jdrefrigeracion.ventas);
ROLLBACK;
-- El acceso denegado de S8
DELETE FROM jdrefrigeracion.detalle_venta WHERE id = -1;
-- Intentar apagar la auditoría que lo vigila
NOAUDIT POLICY pol_sgejd_ventas;
NOAUDIT POLICY pol_sgejd_sesiones BY jd_app;
-- Intentar leer o borrar el rastro
SELECT COUNT(*) FROM unified_audit_trail;
DELETE FROM audsys.aud$unified;
exit
