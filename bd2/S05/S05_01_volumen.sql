-- ============================================================
-- BD2 · S5 · Índices · SGE-JD
-- Paso 1: volumen para medir. Como JDREFRIGERACION, después de S4:
--   .\bd2\herramientas\sql.ps1 bd2\S05\S05_01_volumen.sql
--
-- 1) Cobranza realista sobre la carga de S4: las ventas a CRÉDITO empiezan PENDIENTE y se pagan las que tienen
--    más de 30 días (S4 las había cargado todas PAGADO). Cada cambio pasa por TRG_VENTA_AUDITORIA (S2), así
--    que VENTA_AUDITORIA crece con actividad real del negocio.
-- 2) 20 000 cobros con N° de operación, escritos como los escribe una persona: a veces con minúsculas o
--    espacios de más (por eso CobroRepository compara UPPER(TRIM(referencia))).
-- Todo lo cargado lleva 'Carga BD2 S5' para poder borrarlo.
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 timing on serveroutput on

PROMPT === Cobranza de las ventas a crédito de la carga (cada cambio queda auditado) ===
-- Solo la primera vez: si ya hay ventas a crédito de la carga en PENDIENTE, la cobranza ya se simuló
DECLARE
    v_hecha NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_hecha FROM ventas
    WHERE vendedor_nombre = 'Carga BD2 S4' AND tipo_pago = 'CREDITO' AND estado_pago = 'PENDIENTE';
    IF v_hecha > 0 THEN
        DBMS_OUTPUT.PUT_LINE('La cobranza ya estaba simulada (' || v_hecha || ' a crédito pendientes): no se repite.');
        RETURN;
    END IF;
    UPDATE ventas SET estado_pago = 'PENDIENTE'
    WHERE vendedor_nombre = 'Carga BD2 S4' AND tipo_pago = 'CREDITO' AND estado = 'REGISTRADA' AND estado_pago = 'PAGADO';
    DBMS_OUTPUT.PUT_LINE('A crédito pasadas a PENDIENTE: ' || SQL%ROWCOUNT);
    UPDATE ventas SET estado_pago = 'PAGADO'
    WHERE vendedor_nombre = 'Carga BD2 S4' AND tipo_pago = 'CREDITO' AND estado = 'REGISTRADA'
      AND estado_pago = 'PENDIENTE' AND fecha_venta < TRUNC(SYSDATE) - 30;
    DBMS_OUTPUT.PUT_LINE('Pagadas (más de 30 días): ' || SQL%ROWCOUNT);
    COMMIT;
END;
/

PROMPT === 20 000 cobros (borra antes los de una corrida anterior) ===
DELETE FROM cobros WHERE registrado_por = 'Carga BD2 S5';
DECLARE
    TYPE t_ids IS TABLE OF NUMBER;
    TYPE t_txt IS TABLE OF VARCHAR2(20);
    v_clientes t_ids;
    v_metodos  t_txt := t_txt('TRANSFERENCIA', 'DEPOSITO', 'YAPE_PLIN', 'EFECTIVO', 'CHEQUE');
    v_metodo   VARCHAR2(20);
    v_ref      VARCHAR2(100);
    v_estado   VARCHAR2(10);
    v_r        NUMBER;
    v_cliente  NUMBER;
    v_fecha    DATE;
BEGIN
    SELECT id BULK COLLECT INTO v_clientes FROM clientes;
    DBMS_RANDOM.SEED(2027);
    FOR i IN 1 .. 20000 LOOP
        v_metodo := v_metodos(TRUNC(DBMS_RANDOM.VALUE(1, 6)));
        -- N° de operación único por cobro; como lo teclea Lucy: 80 % limpio, 10 % en minúsculas, 10 % con espacios
        v_ref := 'OP-' || LPAD(TO_CHAR(1000000 + i), 8, '0');
        v_r := DBMS_RANDOM.VALUE;
        IF v_metodo = 'EFECTIVO' THEN v_ref := NULL;
        ELSIF v_r < 0.10 THEN v_ref := LOWER(v_ref);
        ELSIF v_r < 0.20 THEN v_ref := ' ' || v_ref || '  ';
        END IF;
        v_estado := CASE WHEN DBMS_RANDOM.VALUE < 0.02 THEN 'ANULADO' ELSE 'VIGENTE' END;
        -- (como en S4: .COUNT de una colección no se puede usar dentro del INSERT)
        v_cliente := v_clientes(TRUNC(DBMS_RANDOM.VALUE(1, v_clientes.COUNT + 1)));
        v_fecha   := DATE '2024-01-01' + TRUNC(DBMS_RANDOM.VALUE(0, TRUNC(SYSDATE) - DATE '2024-01-01' + 1));
        INSERT INTO cobros (fecha_cobro, metodo_pago, moneda, monto_total, referencia, cliente_id, estado,
                            fecha_anulacion, motivo_anulacion, anulado_por, registrado_por)
        VALUES (v_fecha, v_metodo, 'SOLES', ROUND(DBMS_RANDOM.VALUE(50, 5000), 2), v_ref, v_cliente, v_estado,
                CASE WHEN v_estado = 'ANULADO' THEN TRUNC(SYSDATE) END,
                CASE WHEN v_estado = 'ANULADO' THEN 'Voucher duplicado' END,
                CASE WHEN v_estado = 'ANULADO' THEN 'lisandro' END,
                'Carga BD2 S5');
    END LOOP;
    COMMIT;
END;
/

PROMPT === Estadísticas al día (lección de S4) ===
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'DETALLE_VENTA');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'COBROS');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTA_AUDITORIA');

column table_name format a16
SELECT table_name, num_rows, TO_CHAR(last_analyzed, 'DD/MM HH24:MI') AS analizada
FROM user_tab_statistics WHERE table_name IN ('VENTAS', 'DETALLE_VENTA', 'COBROS', 'VENTA_AUDITORIA') ORDER BY 1;
SELECT accion, COUNT(*) AS filas FROM venta_auditoria GROUP BY accion ORDER BY 1;
exit
