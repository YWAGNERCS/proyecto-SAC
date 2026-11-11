-- ============================================================
-- BD2 · S11 · Paso 3: purgar la auditoría vieja. DROP PARTITION frente a DELETE, medidos.
-- Como JDREFRIGERACION:   .\bd2\herramientas\sql.ps1 bd2\S11\S11_03_drop_frente_a_delete.sql
--
-- Tabla candidata: VENTA_AUDITORIA (S2). Es la que más crece (una fila por cada INSERT/UPDATE/DELETE de una venta)
-- y la única de ventas que SÍ tiene vida útil: se conserva un tiempo y después se purga por antigüedad.
-- Se construyen dos gemelas con el mismo volumen sintético (240 000 eventos en 24 meses, 10 000 por mes):
--   AUDITORIA_PART, particionada por mes (INTERVAL) sobre FECHA_EVENTO, y AUDITORIA_PLANA, sin particionar.
-- Las dos con clave primaria (índice GLOBAL en la particionada) y un índice por venta (LOCAL en la particionada).
-- Se purgan los 12 meses más viejos (120 000 filas) de cada una.
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 timing on serveroutput on
whenever sqlerror continue
column index_name format a26
DROP TABLE auditoria_part PURGE;
DROP TABLE auditoria_plana PURGE;

CREATE TABLE auditoria_part (
    id_auditoria NUMBER, id_venta NUMBER NOT NULL, accion VARCHAR2(10) NOT NULL,
    estado_anterior VARCHAR2(20), estado_nuevo VARCHAR2(20), fecha_evento TIMESTAMP NOT NULL, usuario_oracle VARCHAR2(128))
PARTITION BY RANGE (fecha_evento) INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
  (PARTITION p_inicio VALUES LESS THAN (TIMESTAMP '2024-12-01 00:00:00')) TABLESPACE ts_sgejd_ventas;
CREATE TABLE auditoria_plana (
    id_auditoria NUMBER, id_venta NUMBER NOT NULL, accion VARCHAR2(10) NOT NULL,
    estado_anterior VARCHAR2(20), estado_nuevo VARCHAR2(20), fecha_evento TIMESTAMP NOT NULL, usuario_oracle VARCHAR2(128))
  TABLESPACE ts_sgejd_ventas;

PROMPT ===== Carga: 240 000 eventos entre dic-2024 y nov-2026 (las dos tablas, mismos datos) =====
INSERT /*+ APPEND */ INTO auditoria_part
SELECT LEVEL, MOD(LEVEL, 27000) + 1,
       CASE MOD(LEVEL, 10) WHEN 0 THEN 'INSERT' WHEN 1 THEN 'DELETE' ELSE 'UPDATE' END,
       'REGISTRADA', CASE WHEN MOD(LEVEL, 33) = 0 THEN 'ANULADA' ELSE 'REGISTRADA' END,
       TIMESTAMP '2024-12-01 00:00:00' + NUMTODSINTERVAL(TRUNC((LEVEL - 1) / 10000) * 30.4 + MOD(LEVEL, 10000) / 10000 * 30, 'DAY'),
       'JD_APP'
FROM dual CONNECT BY LEVEL <= 240000;
COMMIT;
INSERT /*+ APPEND */ INTO auditoria_plana SELECT * FROM auditoria_part;
COMMIT;
-- La PK de la particionada es un índice GLOBAL (no se particiona por fecha).
-- OJO: en SQL*Plus no se puede poner un comentario "--" después del ";" en la misma línea: el ";" deja de
-- terminar la sentencia y se pega con la siguiente (ORA-03405, primera corrida de este script).
ALTER TABLE auditoria_part  ADD CONSTRAINT pk_auditoria_part  PRIMARY KEY (id_auditoria);
ALTER TABLE auditoria_plana ADD CONSTRAINT pk_auditoria_plana PRIMARY KEY (id_auditoria);
CREATE INDEX ix_auditoria_part_venta  ON auditoria_part (id_venta) LOCAL;
CREATE INDEX ix_auditoria_plana_venta ON auditoria_plana (id_venta);
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'AUDITORIA_PART');
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'AUDITORIA_PLANA');
SELECT COUNT(*) AS particiones FROM user_tab_partitions WHERE table_name = 'AUDITORIA_PART';
SELECT COUNT(*) AS filas_a_purgar FROM auditoria_plana WHERE fecha_evento < TIMESTAMP '2025-12-01 00:00:00';

PROMPT ===== A) DELETE de los 12 meses más viejos en la tabla sin particionar =====
DELETE FROM auditoria_plana WHERE fecha_evento < TIMESTAMP '2025-12-01 00:00:00';
COMMIT;

PROMPT ===== B) DROP de las particiones de esos mismos 12 meses (sin mantener índices globales) =====
DECLARE
    v_ini PLS_INTEGER := DBMS_UTILITY.GET_TIME;
    v_n   PLS_INTEGER := 0;
BEGIN
    FOR p IN (SELECT partition_name, high_value FROM user_tab_partitions WHERE table_name = 'AUDITORIA_PART') LOOP
        -- HIGH_VALUE es LONG: se evalúa y se compara con el corte
        DECLARE v_limite TIMESTAMP;
        BEGIN
            EXECUTE IMMEDIATE 'SELECT ' || p.high_value || ' FROM dual' INTO v_limite;
            IF v_limite <= TIMESTAMP '2025-12-01 00:00:00' THEN
                EXECUTE IMMEDIATE 'ALTER TABLE auditoria_part DROP PARTITION ' || p.partition_name;
                v_n := v_n + 1;
            END IF;
        END;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Particiones eliminadas: ' || v_n || ' en ' || (DBMS_UTILITY.GET_TIME - v_ini) * 10 || ' ms');
END;
/
PROMPT ===== ¿Qué les pasó a los índices? =====
SELECT index_name, partitioned, status FROM user_indexes WHERE table_name IN ('AUDITORIA_PART', 'AUDITORIA_PLANA') ORDER BY 1;
SELECT COUNT(*) AS particiones_locales_inusables FROM user_ind_partitions WHERE index_name = 'IX_AUDITORIA_PART_VENTA' AND status <> 'USABLE';
INSERT INTO auditoria_part VALUES (999999, 1, 'INSERT', NULL, 'REGISTRADA', SYSTIMESTAMP, 'JD_APP');
ROLLBACK;
exit
