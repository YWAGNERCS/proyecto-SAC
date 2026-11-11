-- BD2 · S10 · Reanuda la auditoría de S9 después de la carga (ver S10_00_pausar_auditoria.sql).
set feedback on
AUDIT POLICY pol_sgejd_ventas;
SELECT policy_name, entity_name FROM audit_unified_enabled_policies WHERE policy_name = 'POL_SGEJD_VENTAS';
exit
