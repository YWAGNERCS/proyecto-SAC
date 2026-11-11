-- ============================================================
-- BD2 · S13 · Backup y recovery con RMAN · SGE-JD
-- Paso 1: ¿la base abrió bien con la imagen nueva? Zonas horarias. Como SYSDBA:
--   .\bd2\herramientas\sql.ps1 bd2\S13\S13_01_zona_horaria.sql -Sysdba
-- ============================================================
set echo off feedback on linesize 200 pagesize 100
column property_name format a28
column property_value format a10
column filename format a20
SELECT * FROM v$timezone_file;
SELECT COUNT(*) AS zonas_horarias FROM v$timezone_names;
SELECT property_name, property_value FROM database_properties WHERE property_name LIKE 'DST%';
ALTER SESSION SET CONTAINER = FREEPDB1;
SELECT name, open_mode FROM v$pdbs;
SELECT COUNT(*) AS zonas_horarias_en_pdb FROM v$timezone_names;
SELECT property_name, property_value FROM database_properties WHERE property_name LIKE 'DST%';
SELECT COUNT(*) AS ventas FROM jdrefrigeracion.ventas;
exit
