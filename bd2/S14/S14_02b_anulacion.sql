-- BD2 · S14 · Sesión B (ventas): anula la MISMA venta con un UPDATE simple, que espera sin límite.
-- Como JD_APP. &1 = id de la venta.
set echo off feedback on linesize 200
EXEC DBMS_APPLICATION_INFO.SET_MODULE('SGE-JD ventas', 'anular venta')
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS b_inicio FROM dual;
UPDATE jdrefrigeracion.ventas
   SET estado = 'ANULADA', motivo_anulacion = 'BD2 S14: prueba de bloqueo', anulado_por = 'practica.s14', fecha_anulacion = SYSDATE
 WHERE id = &1;
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS b_desbloqueada FROM dual;
COMMIT;
exit
