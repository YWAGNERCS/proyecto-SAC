-- BD2 · S13 · Paso 9: ¿qué cambio sobrevivió al PITR? Como JDREFRIGERACION.
set echo off feedback on linesize 200 pagesize 100
column nombre format a40
SELECT id, nombre, costo_base, margen_porcentaje FROM productos WHERE id = (SELECT MIN(id) FROM productos);
SELECT COUNT(*) AS productos, SUM(margen_porcentaje) AS suma_margenes, SUM(costo_base) AS suma_costos FROM productos;
SELECT COUNT(*) AS ventas FROM ventas;
exit
