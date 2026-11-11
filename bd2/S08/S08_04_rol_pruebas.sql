-- ============================================================
-- BD2 · S8 · Paso 4: el DELETE que solo necesitan las PRUEBAS del backend (para limpiar lo que crean).
-- La aplicación no tiene ningún "DELETE FROM" (src/main); las pruebas sí, en 8 tablas.
-- Como SYSTEM:  .\bd2\herramientas\sql.ps1 bd2\S08\S08_04_rol_pruebas.sql -Dba
-- Uso: se otorga a JD_APP SOLO para correr las pruebas en la base de práctica, y se revoca con S08_05.
-- Nunca en desarrollo ni en producción: ahí las pruebas deben limpiar con el dueño o usar una base propia.
-- ============================================================
set echo off feedback on
whenever sqlerror continue
DROP ROLE rol_sgejd_pruebas;
CREATE ROLE rol_sgejd_pruebas;
BEGIN
    FOR t IN (SELECT column_value AS tabla FROM TABLE(sys.odcivarchar2list(
                'COMPROBANTES', 'DETALLE_VENTA', 'ENVIOS_CORREO', 'ENVIOS_SUNAT', 'HISTORIAL_CAMBIOS',
                'INTENTOS_CORREO', 'PRODUCTOS', 'VENTAS'))) LOOP
        EXECUTE IMMEDIATE 'GRANT DELETE ON jdrefrigeracion.' || t.tabla || ' TO rol_sgejd_pruebas';
    END LOOP;
END;
/
GRANT rol_sgejd_pruebas TO jd_app;
ALTER USER jd_app DEFAULT ROLE rol_sgejd_maestros, rol_sgejd_operacion, rol_sgejd_pruebas;
exit
