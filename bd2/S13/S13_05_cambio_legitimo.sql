-- BD2 · S13 · Paso 5: cambio LEGÍTIMO sobre una tabla del proyecto (después del nivel 0). Como JDREFRIGERACION.
-- Sube 5 % el costo base del producto de menor ID (como haría una actualización de costos de proveedor).
set echo off feedback on linesize 200 pagesize 100
column nombre format a40
SELECT id, nombre, costo_base, margen_porcentaje FROM productos WHERE id = (SELECT MIN(id) FROM productos);
UPDATE productos SET costo_base = ROUND(costo_base * 1.05, 2) WHERE id = (SELECT MIN(id) FROM productos);
COMMIT;
SELECT id, nombre, costo_base, margen_porcentaje FROM productos WHERE id = (SELECT MIN(id) FROM productos);
SELECT COUNT(*) AS productos, SUM(margen_porcentaje) AS suma_margenes, SUM(costo_base) AS suma_costos FROM productos;
SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') AS hora_cambio_1 FROM dual;
exit
