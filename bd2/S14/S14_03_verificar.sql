-- BD2 · S14 · Paso 3: ¿qué cambio sobrevivió al bloqueo? Como JDREFRIGERACION. &1 = id de la venta.
set echo off feedback on linesize 200 pagesize 100
column motivo_anulacion format a30
column anulado_por format a14
SELECT id, estado, estado_pago, motivo_anulacion, anulado_por, fecha_anulacion FROM ventas WHERE id = &1;
column usuario_oracle format a12
SELECT accion, estado_anterior, estado_nuevo, usuario_oracle, TO_CHAR(fecha_evento, 'HH24:MI:SS') AS hora
FROM venta_auditoria WHERE id_venta = &1 ORDER BY fecha_evento;
exit
