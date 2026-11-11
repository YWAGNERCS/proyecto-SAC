-- BD2 · S14 · Paso 7: rendimiento en vivo con V$SYSSTAT. Como SYSTEM (-Dba).
-- V$SYSSTAT acumula desde el arranque. Por eso se mide (1) el acumulado y (2) la DIFERENCIA alrededor de una
-- carga conocida: 20 veces el reporte de rentabilidad por producto y mes (5 tablas, como S4), con caché fría y caliente.
set echo off feedback on linesize 200 pagesize 100 serveroutput on
column name format a40
SELECT TO_CHAR(startup_time, 'YYYY-MM-DD HH24:MI:SS') AS arranque, ROUND((SYSDATE - startup_time) * 1440) AS minutos FROM v$instance;
PROMPT ===== 1) Hit ratio acumulado desde el arranque =====
SELECT ROUND((1 - phy.value / (cur.value + con.value)) * 100, 2) AS buffer_cache_hit_ratio,
       phy.value AS physical_reads, cur.value + con.value AS lecturas_logicas
FROM v$sysstat phy, v$sysstat cur, v$sysstat con
WHERE phy.name = 'physical reads' AND cur.name = 'db block gets' AND con.name = 'consistent gets';
PROMPT ===== 2) La misma medida, solo alrededor de la carga =====
ALTER SYSTEM FLUSH BUFFER_CACHE;
DECLARE
    TYPE t_val IS TABLE OF NUMBER INDEX BY VARCHAR2(64);
    a t_val; b t_val;
    v_n NUMBER; v_ini PLS_INTEGER;
    PROCEDURE foto(x IN OUT t_val) IS
    BEGIN
        FOR r IN (SELECT name, value FROM v$sysstat WHERE name IN ('physical reads', 'db block gets', 'consistent gets',
                  'parse count (total)', 'parse count (hard)', 'execute count')) LOOP
            x(r.name) := r.value;
        END LOOP;
    END;
    PROCEDURE informe(etiqueta VARCHAR2) IS
        v_log NUMBER := (b('db block gets') - a('db block gets')) + (b('consistent gets') - a('consistent gets'));
        v_fis NUMBER := b('physical reads') - a('physical reads');
    BEGIN
        DBMS_OUTPUT.PUT_LINE(RPAD(etiqueta, 22) || 'lecturas logicas=' || v_log || '  fisicas=' || v_fis ||
            '  hit ratio=' || ROUND((1 - v_fis / NULLIF(v_log, 0)) * 100, 2) || ' %' ||
            '  parse duro=' || (b('parse count (hard)') - a('parse count (hard)')) ||
            ' de ' || (b('parse count (total)') - a('parse count (total)')) ||
            '  tiempo=' || (DBMS_UTILITY.GET_TIME - v_ini) * 10 || ' ms');
    END;
    PROCEDURE carga(veces PLS_INTEGER) IS
    BEGIN
        FOR i IN 1 .. veces LOOP
            SELECT COUNT(*) INTO v_n FROM (
                SELECT p.nombre, TRUNC(v.fecha_venta, 'MM') AS mes, SUM(d.cantidad * d.precio_unitario) AS venta,
                       SUM(d.cantidad * d.costo_unitario) AS costo
                FROM jdrefrigeracion.ventas v
                JOIN jdrefrigeracion.detalle_venta d ON d.venta_id = v.id
                JOIN jdrefrigeracion.productos p ON p.id = d.producto_id
                LEFT JOIN jdrefrigeracion.categorias c ON c.id = p.categoria_id
                JOIN jdrefrigeracion.clientes cl ON cl.id = v.cliente_id
                WHERE v.estado = 'REGISTRADA'
                GROUP BY p.nombre, TRUNC(v.fecha_venta, 'MM'));
        END LOOP;
    END;
BEGIN
    foto(a); v_ini := DBMS_UTILITY.GET_TIME; carga(1);  foto(b); informe('1 vez, cache fria');
    foto(a); v_ini := DBMS_UTILITY.GET_TIME; carga(19); foto(b); informe('19 veces, caliente');
    DBMS_OUTPUT.PUT_LINE('Filas del reporte: ' || v_n);
END;
/
PROMPT ===== 3) Esperas de la instancia desde el arranque (sin las ociosas) =====
column event format a40
SELECT * FROM (
  SELECT event, total_waits, ROUND(time_waited_micro / 1e6, 1) AS segundos
  FROM v$system_event WHERE wait_class <> 'Idle' ORDER BY time_waited_micro DESC)
WHERE ROWNUM <= 8;
SELECT event, total_waits, ROUND(time_waited_micro / 1e6, 1) AS segundos FROM v$system_event WHERE event LIKE 'enq: TX - row lock%';
exit
