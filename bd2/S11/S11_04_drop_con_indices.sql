-- ============================================================
-- BD2 · S11 · Paso 4: el DROP PARTITION correcto, que mantiene el índice global de la PK.
-- Como JDREFRIGERACION, después de S11_03:   .\bd2\herramientas\sql.ps1 bd2\S11\S11_04_drop_con_indices.sql
-- En S11_03 el DROP simple dejó PK_AUDITORIA_PART en UNUSABLE y el INSERT siguiente falló con ORA-01502.
-- Aquí se reconstruye la tabla particionada con los mismos datos y se purga con UPDATE GLOBAL INDEXES.
-- ============================================================
set echo off feedback on linesize 200 pagesize 100 timing on serveroutput on
whenever sqlerror continue
column index_name format a26
DROP TABLE auditoria_part PURGE;
CREATE TABLE auditoria_part (
    id_auditoria NUMBER, id_venta NUMBER NOT NULL, accion VARCHAR2(10) NOT NULL,
    estado_anterior VARCHAR2(20), estado_nuevo VARCHAR2(20), fecha_evento TIMESTAMP NOT NULL, usuario_oracle VARCHAR2(128))
PARTITION BY RANGE (fecha_evento) INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
  (PARTITION p_inicio VALUES LESS THAN (TIMESTAMP '2024-12-01 00:00:00')) TABLESPACE ts_sgejd_ventas;
INSERT /*+ APPEND */ INTO auditoria_part
SELECT LEVEL, MOD(LEVEL, 27000) + 1,
       CASE MOD(LEVEL, 10) WHEN 0 THEN 'INSERT' WHEN 1 THEN 'DELETE' ELSE 'UPDATE' END,
       'REGISTRADA', CASE WHEN MOD(LEVEL, 33) = 0 THEN 'ANULADA' ELSE 'REGISTRADA' END,
       TIMESTAMP '2024-12-01 00:00:00' + NUMTODSINTERVAL(TRUNC((LEVEL - 1) / 10000) * 30.4 + MOD(LEVEL, 10000) / 10000 * 30, 'DAY'),
       'JD_APP'
FROM dual CONNECT BY LEVEL <= 240000;
COMMIT;
ALTER TABLE auditoria_part ADD CONSTRAINT pk_auditoria_part PRIMARY KEY (id_auditoria);
CREATE INDEX ix_auditoria_part_venta ON auditoria_part (id_venta) LOCAL;

PROMPT ===== DROP de los mismos 12 meses, manteniendo los índices globales =====
DECLARE
    v_ini PLS_INTEGER := DBMS_UTILITY.GET_TIME;
    v_n   PLS_INTEGER := 0;
    v_limite TIMESTAMP;
BEGIN
    FOR p IN (SELECT partition_name, high_value FROM user_tab_partitions WHERE table_name = 'AUDITORIA_PART') LOOP
        EXECUTE IMMEDIATE 'SELECT ' || p.high_value || ' FROM dual' INTO v_limite;
        IF v_limite <= TIMESTAMP '2025-12-01 00:00:00' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE auditoria_part DROP PARTITION ' || p.partition_name || ' UPDATE GLOBAL INDEXES';
            v_n := v_n + 1;
        END IF;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Particiones eliminadas: ' || v_n || ' en ' || (DBMS_UTILITY.GET_TIME - v_ini) * 10 || ' ms (con UPDATE GLOBAL INDEXES)');
END;
/
SELECT index_name, partitioned, status FROM user_indexes WHERE table_name = 'AUDITORIA_PART' ORDER BY 1;
SELECT COUNT(*) AS filas_que_quedan FROM auditoria_part;
INSERT INTO auditoria_part VALUES (999999, 1, 'INSERT', NULL, 'REGISTRADA', SYSTIMESTAMP, 'JD_APP');
ROLLBACK;
exit
