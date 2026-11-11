-- ============================================================
-- Migración BD2 · 5/7 · Usuarios y roles de mínimo privilegio (BD2 S1, S7 y S8)
--  * ROL_SGEJD_MAESTROS: catálogo, clientes, sedes y proveedores (los únicos que el backend borra).
--  * ROL_SGEJD_OPERACION: documentos sin DELETE (se anulan); líneas y registros solo INSERT; el motor PL/SQL.
--  * ROL_SGEJD_CONSULTA: lectura para reportes, sin XML, CDR ni correos.
--  * JD_APP: el usuario del backend en producción (sin privilegios directos, solo roles).
--    En desarrollo el backend sigue entrando como el dueño, porque usa ddl-auto=update.
--  * JD_REPORTES: solo lectura, para un Excel o un tablero.
--  * El dueño pierde DB_DEVELOPER_ROLE (que la imagen le dio, con RESOURCE y SODA_APP adentro)
--    y queda con lo justo para mantener su esquema.
-- Las claves llegan como primer argumento (JD_APP) y segundo (JD_REPORTES) desde migrar.ps1 y no se imprimen. A un usuario que
-- ya existe no se le cambia la clave.
-- OJO al agregar una tabla nueva: hay que darle sus privilegios al rol que corresponda (ver docs/README).
-- Lo ejecuta migracion_bd2\migrar.ps1 como SYSDBA. Se puede repetir.
-- ============================================================
whenever sqlerror exit failure
set serveroutput on feedback off verify off
ALTER SESSION SET CONTAINER = FREEPDB1;
-- otra vez: DBMS_OUTPUT se habilita por contenedor
set serveroutput on
DECLARE
    v_n NUMBER;
    PROCEDURE ejecutar(p_sql VARCHAR2, p_mostrar VARCHAR2 DEFAULT NULL) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || NVL(p_mostrar, SUBSTR(p_sql, 1, 110)));
    EXCEPTION WHEN OTHERS THEN
        -- -1920 usuario o rol ya existe, -1921 rol ya existe, -1951/-1952 no estaba concedido
        IF SQLCODE IN (-1920, -1921, -1951, -1952) THEN
            DBMS_OUTPUT.PUT_LINE('  (sin cambio) ' || NVL(p_mostrar, SUBSTR(p_sql, 1, 100)));
        ELSE
            RAISE;
        END IF;
    END;
    PROCEDURE conceder(p_privs VARCHAR2, p_tablas sys.odcivarchar2list, p_rol VARCHAR2) IS
    BEGIN
        FOR i IN 1 .. p_tablas.COUNT LOOP
            EXECUTE IMMEDIATE 'GRANT ' || p_privs || ' ON jdrefrigeracion.' || p_tablas(i) || ' TO ' || p_rol;
        END LOOP;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || p_privs || ' en ' || p_tablas.COUNT || ' tablas para ' || p_rol);
    END;
BEGIN
    ejecutar('CREATE ROLE rol_sgejd_maestros');
    ejecutar('CREATE ROLE rol_sgejd_operacion');
    ejecutar('CREATE ROLE rol_sgejd_consulta');

    conceder('SELECT, INSERT, UPDATE, DELETE',
        sys.odcivarchar2list('CATEGORIAS', 'PRODUCTOS', 'CLIENTES', 'SEDES_CLIENTE', 'PROVEEDORES'), 'rol_sgejd_maestros');
    conceder('SELECT, INSERT, UPDATE',
        sys.odcivarchar2list('VENTAS', 'PROFORMAS', 'COMPRAS', 'COBROS', 'COMPROBANTES', 'NOTAS_CREDITO', 'SERIES_COMPROBANTE',
            'ENVIOS_CORREO', 'DEVOLUCIONES', 'PAGOS_PROVEEDOR', 'ORDENES_TRABAJO', 'MANTENIMIENTOS',
            'EQUIPOS_INSTALADOS', 'CONTRATOS_MANTENIMIENTO', 'TOPES_DESCUENTO'), 'rol_sgejd_operacion');
    conceder('SELECT, INSERT',
        sys.odcivarchar2list('DETALLE_VENTA', 'ITEMS_PROFORMA', 'DETALLE_COMPRA', 'DETALLE_COBRO', 'MATERIALES_ORDEN',
            'AJUSTES_INVENTARIO', 'ENVIOS_SUNAT', 'INTENTOS_CORREO', 'HISTORIAL_CAMBIOS'), 'rol_sgejd_operacion');
    ejecutar('GRANT EXECUTE ON jdrefrigeracion.sp_registrar_venta TO rol_sgejd_operacion');
    ejecutar('GRANT EXECUTE ON jdrefrigeracion.fn_total_venta TO rol_sgejd_operacion');
    conceder('SELECT',
        sys.odcivarchar2list('VENTAS', 'DETALLE_VENTA', 'PRODUCTOS', 'CATEGORIAS', 'CLIENTES', 'PROFORMAS', 'ITEMS_PROFORMA',
            'COMPRAS', 'DETALLE_COMPRA', 'COBROS', 'DETALLE_COBRO', 'COMPROBANTES', 'NOTAS_CREDITO',
            'PROVEEDORES', 'MANTENIMIENTOS', 'ORDENES_TRABAJO', 'DEVOLUCIONES', 'PAGOS_PROVEEDOR'), 'rol_sgejd_consulta');

    SELECT COUNT(*) INTO v_n FROM dba_users WHERE username = 'JD_APP';
    IF v_n = 0 THEN
        ejecutar('CREATE USER jd_app IDENTIFIED BY "&1"', 'CREATE USER jd_app (clave desde el archivo de secretos)');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  JD_APP ya existe: se conserva su clave');
    END IF;
    ejecutar('GRANT CREATE SESSION TO jd_app');
    ejecutar('GRANT rol_sgejd_maestros, rol_sgejd_operacion TO jd_app');
    ejecutar('ALTER USER jd_app DEFAULT ROLE rol_sgejd_maestros, rol_sgejd_operacion');

    SELECT COUNT(*) INTO v_n FROM dba_users WHERE username = 'JD_REPORTES';
    IF v_n = 0 THEN
        ejecutar('CREATE USER jd_reportes IDENTIFIED BY "&2"', 'CREATE USER jd_reportes (clave desde el archivo de secretos)');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  JD_REPORTES ya existe: se conserva su clave');
    END IF;
    ejecutar('GRANT CREATE SESSION TO jd_reportes');
    ejecutar('GRANT rol_sgejd_consulta TO jd_reportes');

    ejecutar('GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO jdrefrigeracion');
    ejecutar('ALTER USER jdrefrigeracion QUOTA UNLIMITED ON users');
    ejecutar('REVOKE db_developer_role FROM jdrefrigeracion');
END;
/
set feedback on
column grantee format a16
column granted_role format a22
SELECT grantee, granted_role, default_role FROM dba_role_privs
WHERE grantee IN ('JD_APP', 'JD_REPORTES', 'JDREFRIGERACION') ORDER BY grantee, granted_role;
SELECT grantee, COUNT(*) AS privilegios_directos_sobre_tablas FROM dba_tab_privs
WHERE grantee IN ('JD_APP', 'JD_REPORTES') GROUP BY grantee;
exit
