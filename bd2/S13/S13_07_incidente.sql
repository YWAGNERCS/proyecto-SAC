-- BD2 · S13 · Paso 7: el INCIDENTE simulado. Como JDREFRIGERACION.
-- Primero se anota la "hora segura" (después del cambio legítimo y del nivel 1, antes del error).
-- Luego un UPDATE sin WHERE deja el margen en 0 en TODO el catálogo: los precios de venta (costo + margen)
-- quedan al costo y cualquier proforma nueva sale sin ganancia. Se confirma con COMMIT, como pasa en la vida real.
set echo off feedback on linesize 200 pagesize 100
SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') AS hora_segura FROM dual;
EXEC DBMS_SESSION.SLEEP(5)
UPDATE productos SET margen_porcentaje = 0;
COMMIT;
SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') AS hora_incidente FROM dual;
SELECT COUNT(*) AS productos, SUM(margen_porcentaje) AS suma_margenes, SUM(costo_base) AS suma_costos FROM productos;
exit
