-- ============================================================
-- BD2 · S7 · Paso 2: la misma consulta de instancia, como usuario de la aplicación.
--   .\bd2\herramientas\sql.ps1 bd2\S07\S07_02_usuario_aplicacion.sql                  (JDREFRIGERACION)
--   .\bd2\herramientas\sql.ps1 bd2\S07\S07_02_usuario_aplicacion.sql -Usuario JD_APP
-- Esperado: ORA-00942 en las vistas V$. No es que la vista no exista: el usuario no tiene privilegio para verla.
-- ============================================================
set echo off feedback off linesize 200 pagesize 100
whenever sqlerror continue
SHOW USER
PROMPT --> SELECT * FROM v$sga
SELECT * FROM v$sga;
PROMPT --> SELECT instance_name FROM v$instance
SELECT instance_name FROM v$instance;
PROMPT --> Lo que este usuario sí puede ver: sus propios privilegios
column privilege format a30
SELECT privilege FROM session_privs ORDER BY privilege;
SELECT role FROM session_roles;
exit
