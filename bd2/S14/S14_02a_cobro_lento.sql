-- BD2 · S14 · Sesión A (cobranza): bloquea la venta como VentaRepository.bloquear() y tarda en confirmar.
-- Se ejecuta como JD_APP, el usuario del backend (S8). &1 = id de la venta, &2 = segundos que retiene el bloqueo.
set echo off feedback on serveroutput on linesize 200
EXEC DBMS_APPLICATION_INFO.SET_MODULE('SGE-JD cobranza', 'registrar cobro')
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS a_inicio FROM dual;
-- Lo que genera Hibernate para PESSIMISTIC_WRITE con lock.timeout = 10000 ms
SELECT id, estado, estado_pago FROM jdrefrigeracion.ventas WHERE id = &1 FOR UPDATE WAIT 10;
UPDATE jdrefrigeracion.ventas SET estado_pago = 'PAGADO' WHERE id = &1;
EXEC DBMS_SESSION.SLEEP(&2)
-- El cobro no se confirma (por ejemplo, voucher duplicado): se revierte
ROLLBACK;
SELECT TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS.FF2') AS a_rollback FROM dual;
exit
