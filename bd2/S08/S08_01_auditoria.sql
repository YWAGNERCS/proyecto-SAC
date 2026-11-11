-- ============================================================
-- BD2 · S8 · Usuarios, roles y privilegios · SGE-JD
-- Paso 1: qué puede hacer HOY cada usuario de la aplicación (diccionario de datos).
-- Como SYSTEM en FREEPDB1:   .\bd2\herramientas\sql.ps1 bd2\S08\S08_01_auditoria.sql -Dba
-- ============================================================
set echo off feedback off linesize 200 pagesize 200
column grantee format a16
column privilege format a32
column granted_role format a22
column owner format a16
column table_name format a22

PROMPT ===== Usuarios del proyecto =====
SELECT username, account_status, default_tablespace, TO_CHAR(created, 'DD/MM/YYYY') AS creado
FROM dba_users WHERE username IN ('JDREFRIGERACION', 'JD_APP', 'JD_REPORTES') ORDER BY username;

PROMPT ===== Privilegios de sistema directos (DBA_SYS_PRIVS) =====
SELECT grantee, privilege, admin_option FROM dba_sys_privs
WHERE grantee IN ('JDREFRIGERACION', 'JD_APP') ORDER BY grantee, privilege;

PROMPT ===== Roles otorgados (DBA_ROLE_PRIVS) =====
SELECT grantee, granted_role, default_role FROM dba_role_privs
WHERE grantee IN ('JDREFRIGERACION', 'JD_APP') ORDER BY grantee, granted_role;

PROMPT ===== Lo que traen esos roles (privilegios de sistema por rol) =====
SELECT grantee AS rol, COUNT(*) AS privilegios_de_sistema FROM dba_sys_privs
WHERE grantee IN ('DB_DEVELOPER_ROLE', 'RESOURCE', 'SODA_APP') GROUP BY grantee ORDER BY grantee;
SELECT privilege FROM dba_sys_privs WHERE grantee = 'DB_DEVELOPER_ROLE' ORDER BY privilege;

PROMPT ===== Privilegios de objeto (DBA_TAB_PRIVS) =====
SELECT grantee, owner, table_name, privilege FROM dba_tab_privs
WHERE grantee IN ('JDREFRIGERACION', 'JD_APP') ORDER BY grantee, table_name, privilege;

PROMPT ===== Cuota de espacio del dueño (DBA_TS_QUOTAS; -1 = ilimitada) =====
SELECT username, tablespace_name, max_bytes FROM dba_ts_quotas WHERE username IN ('JDREFRIGERACION', 'JD_APP');
SELECT grantee, privilege FROM dba_sys_privs WHERE privilege = 'UNLIMITED TABLESPACE' AND grantee IN ('JDREFRIGERACION', 'JD_APP');

PROMPT ===== Lo que el dueño tiene en su esquema =====
SELECT object_type, COUNT(*) AS objetos FROM dba_objects WHERE owner = 'JDREFRIGERACION' GROUP BY object_type ORDER BY 1;
exit
