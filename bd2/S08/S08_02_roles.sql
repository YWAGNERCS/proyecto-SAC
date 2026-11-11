-- ============================================================
-- BD2 · S8 · Paso 2: roles por función, JD_APP reasignado y JD_REPORTES de solo lectura.
-- Como SYSTEM en FREEPDB1:   .\bd2\herramientas\sql.ps1 bd2\S08\S08_02_roles.sql -Dba
-- Se puede repetir (borra los roles y JD_REPORTES antes de crearlos).
-- La clave de JD_REPORTES es la de JD_APP solo en esta base de práctica; se pasa como &1 (-ClaveApp).
--
-- De dónde salen los privilegios (inventario del backend, sección 2 del README):
--   * DELETE solo existe en 5 servicios: CategoriaService, ProductoService, ClienteService, SedeClienteService,
--     ProveedorService. Ningún otro módulo borra filas (ni por orphanRemoval: nadie quita líneas de una colección).
--   * Las líneas (DETALLE_*, ITEMS_PROFORMA, MATERIALES_ORDEN) y los registros (HISTORIAL_CAMBIOS, ENVIOS_SUNAT,
--     INTENTOS_CORREO, AJUSTES_INVENTARIO) se insertan y no se modifican: sin UPDATE.
--   * El resto se inserta y se actualiza (save de Spring Data): SELECT, INSERT, UPDATE.
--   * VENTA_AUDITORIA y LOG_ERRORES (BD2 S2-S3) los escriben un trigger y un procedimiento con los derechos
--     del dueño: la aplicación no necesita ningún privilegio sobre ellas.
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 verify off
whenever sqlerror continue
DEFINE clave = "&1"

DROP ROLE rol_sgejd_maestros;
DROP ROLE rol_sgejd_operacion;
DROP ROLE rol_sgejd_consulta;
DROP USER jd_reportes CASCADE;

PROMPT ===== ROL_SGEJD_MAESTROS: datos maestros que la aplicación crea, edita y elimina =====
CREATE ROLE rol_sgejd_maestros;
GRANT SELECT, INSERT, UPDATE, DELETE ON jdrefrigeracion.categorias    TO rol_sgejd_maestros;
GRANT SELECT, INSERT, UPDATE, DELETE ON jdrefrigeracion.productos     TO rol_sgejd_maestros;
GRANT SELECT, INSERT, UPDATE, DELETE ON jdrefrigeracion.clientes      TO rol_sgejd_maestros;
GRANT SELECT, INSERT, UPDATE, DELETE ON jdrefrigeracion.sedes_cliente TO rol_sgejd_maestros;
GRANT SELECT, INSERT, UPDATE, DELETE ON jdrefrigeracion.proveedores   TO rol_sgejd_maestros;

PROMPT ===== ROL_SGEJD_OPERACION: el día a día (vender, facturar, cobrar, comprar, atender) =====
CREATE ROLE rol_sgejd_operacion;
-- Documentos que cambian de estado: sin DELETE (se anulan, no se borran)
BEGIN
    FOR t IN (SELECT column_value AS tabla FROM TABLE(sys.odcivarchar2list(
                'VENTAS', 'PROFORMAS', 'COMPRAS', 'COBROS', 'COMPROBANTES', 'NOTAS_CREDITO', 'SERIES_COMPROBANTE',
                'ENVIOS_CORREO', 'DEVOLUCIONES', 'PAGOS_PROVEEDOR', 'ORDENES_TRABAJO', 'MANTENIMIENTOS',
                'EQUIPOS_INSTALADOS', 'CONTRATOS_MANTENIMIENTO', 'TOPES_DESCUENTO'))) LOOP
        EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE ON jdrefrigeracion.' || t.tabla || ' TO rol_sgejd_operacion';
    END LOOP;
    -- Líneas y registros: se insertan y no se tocan más
    FOR t IN (SELECT column_value AS tabla FROM TABLE(sys.odcivarchar2list(
                'DETALLE_VENTA', 'ITEMS_PROFORMA', 'DETALLE_COMPRA', 'DETALLE_COBRO', 'MATERIALES_ORDEN',
                'AJUSTES_INVENTARIO', 'ENVIOS_SUNAT', 'INTENTOS_CORREO', 'HISTORIAL_CAMBIOS'))) LOOP
        EXECUTE IMMEDIATE 'GRANT SELECT, INSERT ON jdrefrigeracion.' || t.tabla || ' TO rol_sgejd_operacion';
    END LOOP;
END;
/
-- El motor PL/SQL de BD2 S1-S3 (antes concedido directo a JD_APP)
GRANT EXECUTE ON jdrefrigeracion.sp_registrar_venta TO rol_sgejd_operacion;
GRANT EXECUTE ON jdrefrigeracion.fn_total_venta     TO rol_sgejd_operacion;

PROMPT ===== ROL_SGEJD_CONSULTA: solo lectura de lo que sirve para reportes (sin XML, CDR ni correos) =====
CREATE ROLE rol_sgejd_consulta;
BEGIN
    FOR t IN (SELECT column_value AS tabla FROM TABLE(sys.odcivarchar2list(
                'VENTAS', 'DETALLE_VENTA', 'PRODUCTOS', 'CATEGORIAS', 'CLIENTES', 'PROFORMAS', 'ITEMS_PROFORMA',
                'COMPRAS', 'DETALLE_COMPRA', 'COBROS', 'DETALLE_COBRO', 'COMPROBANTES', 'NOTAS_CREDITO',
                'PROVEEDORES', 'MANTENIMIENTOS', 'ORDENES_TRABAJO', 'DEVOLUCIONES', 'PAGOS_PROVEEDOR'))) LOOP
        EXECUTE IMMEDIATE 'GRANT SELECT ON jdrefrigeracion.' || t.tabla || ' TO rol_sgejd_consulta';
    END LOOP;
END;
/

PROMPT ===== JD_APP: primero los roles, después se revoca lo directo (nunca queda sin acceso) =====
GRANT rol_sgejd_maestros, rol_sgejd_operacion TO jd_app;
ALTER USER jd_app DEFAULT ROLE rol_sgejd_maestros, rol_sgejd_operacion;
REVOKE EXECUTE ON jdrefrigeracion.sp_registrar_venta FROM jd_app;
REVOKE EXECUTE ON jdrefrigeracion.fn_total_venta     FROM jd_app;

PROMPT ===== JD_REPORTES: usuario de solo lectura (para Excel o un tablero de Lisandro) =====
CREATE USER jd_reportes IDENTIFIED BY "&clave";
GRANT CREATE SESSION TO jd_reportes;
GRANT rol_sgejd_consulta TO jd_reportes;

PROMPT ===== El dueño: solo lo que necesita para mantener su esquema =====
-- La imagen de Docker le dio DB_DEVELOPER_ROLE, que trae anidados RESOURCE y este a SODA_APP (por eso
-- SESSION_ROLES mostraba tres en S7): se revoca solo el de arriba y caen los tres.
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO jdrefrigeracion;
ALTER USER jdrefrigeracion QUOTA UNLIMITED ON users;
REVOKE db_developer_role FROM jdrefrigeracion;
UNDEFINE clave
exit
