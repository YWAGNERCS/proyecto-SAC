-- ============================================================
-- BD2 · S8 · Paso 3: los límites, probados a propósito. Se corre dos veces:
--   .\bd2\herramientas\sql.ps1 bd2\S08\S08_03_limites.sql -Usuario JD_APP
--   .\bd2\herramientas\sql.ps1 bd2\S08\S08_03_limites.sql -Usuario JD_REPORTES
-- Lo que debe funcionar, funciona; lo que no, responde ORA-01031 (o ORA-00942 si ni siquiera puede verlo).
-- Todo se deshace al final (ROLLBACK): ninguna prueba deja datos.
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue
SHOW USER
SELECT role FROM session_roles ORDER BY role;

-- Lectura de ventas (los dos deberían poder)
SELECT COUNT(*) AS ventas FROM jdrefrigeracion.ventas;

-- Borrar una venta: la aplicación nunca borra ventas (se anulan)
DELETE FROM jdrefrigeracion.ventas WHERE id = -1;

-- Borrar líneas de una venta
DELETE FROM jdrefrigeracion.detalle_venta WHERE id = -1;

-- Modificar un producto: JD_APP sí (catálogo); JD_REPORTES no
UPDATE jdrefrigeracion.productos SET stock_actual = stock_actual WHERE id = -1;

-- Leer la auditoría de S2 o el registro de errores de S3: nadie de la aplicación
SELECT COUNT(*) FROM jdrefrigeracion.venta_auditoria;
SELECT COUNT(*) FROM jdrefrigeracion.log_errores;

-- Crear objetos: ninguno de los dos
CREATE TABLE prueba_s8 (id NUMBER);

-- Desactivar el trigger de auditoría (lo que haría alguien para borrar su rastro)
ALTER TRIGGER jdrefrigeracion.trg_venta_auditoria DISABLE;

-- Usar el motor PL/SQL de S1 (JD_APP sí, por el rol; JD_REPORTES no)
SELECT jdrefrigeracion.fn_total_venta(MIN(venta_id)) AS total_primera_venta FROM jdrefrigeracion.detalle_venta;
ROLLBACK;
exit
