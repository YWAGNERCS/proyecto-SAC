-- BD2 · S14 · Sesión B' (ventas): la misma anulación, pero como la hace el backend: primero
-- VentaRepository.bloquear() = SELECT ... FOR UPDATE WAIT 10. No espera para siempre. Como JD_APP. &1 = id.
set echo off feedback on linesize 200
whenever sqlerror continue
EXEC DBMS_APPLICATION_INFO.SET_MODULE('SGE-JD ventas', 'anular venta (backend)')
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS b_inicio FROM dual;
SELECT id, estado FROM jdrefrigeracion.ventas WHERE id = &1 FOR UPDATE WAIT 10;
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS b_se_rinde FROM dual;
ROLLBACK;
exit
