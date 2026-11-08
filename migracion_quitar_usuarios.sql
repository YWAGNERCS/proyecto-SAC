-- ============================================================
-- Migración: borra la tabla USUARIOS (auditoría B7, 07/11/2026)
--
-- Desde S10b los usuarios y contraseñas viven en Keycloak: ninguna entidad ni consulta usa USUARIOS.
-- Las bases creadas con scripts/prod/esquema_jdrefrigeracion.sql ya no la tienen; las de desarrollo y el
-- respaldo de entrega anterior, sí.
--
-- Por seguridad solo la borra si está VACÍA: si tiene filas, no hace nada y lo dice.
--
-- Uso (idempotente: si la tabla ya no existe, no hace nada):
--   docker cp migracion_quitar_usuarios.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
-- ============================================================
SET SERVEROUTPUT ON
DECLARE
    v_esquema VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    v_existe  NUMBER;
    v_filas   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe FROM all_tables WHERE owner = v_esquema AND table_name = 'USUARIOS';
    IF v_existe = 0 THEN
        DBMS_OUTPUT.PUT_LINE('USUARIOS: no existe (nada que hacer)');
        RETURN;
    END IF;
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || v_esquema || '.USUARIOS' INTO v_filas;
    IF v_filas > 0 THEN
        DBMS_OUTPUT.PUT_LINE('USUARIOS: tiene ' || v_filas || ' fila(s); NO se borra. Revísela antes de borrarla a mano.');
        RETURN;
    END IF;
    EXECUTE IMMEDIATE 'DROP TABLE ' || v_esquema || '.USUARIOS CASCADE CONSTRAINTS PURGE';
    DBMS_OUTPUT.PUT_LINE('USUARIOS: borrada (estaba vacía)');
END;
/
