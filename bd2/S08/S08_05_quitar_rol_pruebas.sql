-- BD2 · S8 · Paso 5: JD_APP vuelve a sus dos roles de aplicación (sin el DELETE de las pruebas).
--   .\bd2\herramientas\sql.ps1 bd2\S08\S08_05_quitar_rol_pruebas.sql -Dba
set echo off feedback on
REVOKE rol_sgejd_pruebas FROM jd_app;
ALTER USER jd_app DEFAULT ROLE rol_sgejd_maestros, rol_sgejd_operacion;
exit
