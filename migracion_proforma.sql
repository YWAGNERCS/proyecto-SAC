-- ============================================================
-- Migración: "Cotización" pasa a llamarse "Proforma" (13/10/2026)
-- Ejecutar UNA vez en cada base local ANTES de arrancar el backend nuevo.
-- Conserva todos los datos: solo renombra tablas y columnas.
--
--   docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1 < migracion_proforma.sql
--
-- Si no se ejecuta, Hibernate (ddl-auto=update) crearía tablas PROFORMAS vacías
-- y las proformas existentes "desaparecerían" de la aplicación.
-- El módulo Postventa NO requiere migración: solo cambió el paquete Java,
-- la tabla sigue siendo MANTENIMIENTOS.
-- ============================================================

ALTER TABLE COTIZACIONES RENAME TO PROFORMAS;
ALTER TABLE ITEMS_COTIZACION RENAME TO ITEMS_PROFORMA;
ALTER TABLE ITEMS_PROFORMA RENAME COLUMN COTIZACION_ID TO PROFORMA_ID;
ALTER TABLE VENTAS RENAME COLUMN COTIZACION_ID TO PROFORMA_ID;
