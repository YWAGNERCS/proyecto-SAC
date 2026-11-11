-- BD2 · S10 · Antes y después de la carga de prueba. Como SYSTEM:
--   .\bd2\herramientas\sql.ps1 bd2\S10\S10_00_pausar_auditoria.sql -Dba          (pausa)
--   .\bd2\herramientas\sql.ps1 bd2\S10\S10_00_reanudar_auditoria.sql -Dba        (reanuda)
-- La política de S9 audita INSERT sobre VENTAS y DETALLE_VENTA de cualquier usuario: sin pausarla, la carga
-- dejaría ~120 000 filas de ruido en UNIFIED_AUDIT_TRAIL. El NOAUDIT queda registrado igual (ORA$MANDATORY).
set feedback on
NOAUDIT POLICY pol_sgejd_ventas;
exit
