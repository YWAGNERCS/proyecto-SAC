-- BD2 · S3 · Sesión A del paso 5 (la lanza S03_05_bloqueo.ps1): bloquea el producto 20 s sin confirmar.
set feedback off serveroutput on
UPDATE productos SET stock_actual = stock_actual
WHERE id = (SELECT id FROM (SELECT id FROM productos
                            WHERE sector = 'MATERIAL_SERVICIO' AND moneda = 'SOLES' AND stock_actual >= 5
                            ORDER BY costo_base * (1 + margen_porcentaje / 100), id) WHERE ROWNUM = 1);
EXEC DBMS_OUTPUT.PUT_LINE('Sesion A: producto bloqueado a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS'));
EXEC DBMS_SESSION.SLEEP(20);
ROLLBACK;
EXEC DBMS_OUTPUT.PUT_LINE('Sesion A: libera el producto a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS'));
exit
