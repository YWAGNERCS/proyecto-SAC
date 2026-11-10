-- ============================================================
-- BD2 · S1 · PL/SQL aplicado al negocio · SGE-JD (JD Refrigeración S.A.C.)
-- Paso 1: esquema propietario y usuario técnico de la aplicación.
-- Se ejecuta como SYSTEM sobre FREEPDB1 (base de práctica sge-jd-bd2):
--   .\bd2\herramientas\sql.ps1 bd2\S01\S01_01_esquema.sql -Dba
--
-- JDREFRIGERACION ya existe: es el esquema propietario de las 29 tablas de SGE-JD (lo crea el contenedor
-- con APP_USER y LP2 lo llena). Aquí se verifica y se crea JD_APP, el usuario técnico que en S8 reemplazará
-- al propietario en el backend: solo puede conectarse; no es dueño de nada.
-- La clave de JD_APP se pasa con la variable APP_PW (sale de bd2/.env.bd2, nunca se escribe aquí).
-- ============================================================
set echo on feedback on linesize 200 pagesize 100 verify off
whenever sqlerror continue

-- El propietario: dueño de las tablas, puede crear objetos PL/SQL
SELECT username, account_status, default_tablespace, created
FROM dba_users
WHERE username IN ('JDREFRIGERACION', 'JD_APP')
ORDER BY username;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE TRIGGER, CREATE SEQUENCE
    TO JDREFRIGERACION;

-- El usuario técnico (si ya existe, ORA-01920 y se deja como está)
DEFINE app_pw = "&1"
CREATE USER JD_APP IDENTIFIED BY "&app_pw";
GRANT CREATE SESSION TO JD_APP;
UNDEFINE app_pw

SELECT grantee, privilege
FROM dba_sys_privs
WHERE grantee IN ('JDREFRIGERACION', 'JD_APP')
ORDER BY grantee, privilege;
exit
