-- BD2 · S5 · Sesión A del paso 4 (la lanza S05_04_bitmap_concurrencia.ps1): registra una fila de auditoría
-- (como lo hace TRG_VENTA_AUDITORIA cuando Lucy guarda una venta) y tarda 15 s en confirmar.
set feedback off serveroutput on
INSERT INTO venta_auditoria (id_venta, accion, estado_nuevo) VALUES (-1, 'UPDATE', 'PRUEBA S5');
EXEC DBMS_OUTPUT.PUT_LINE('Sesion A: insertó su fila de auditoría a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS'));
EXEC DBMS_SESSION.SLEEP(15);
ROLLBACK;
EXEC DBMS_OUTPUT.PUT_LINE('Sesion A: terminó su transacción a las ' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS'));
exit
