-- ============================================================
-- BD2 · S11 · Paso 2: las variantes Hash, List y Composite, con los datos reales de VENTAS y su verificación en
-- el diccionario. Como JDREFRIGERACION:   .\bd2\herramientas\sql.ps1 bd2\S11\S11_02_variantes.sql
-- ============================================================
set echo off feedback on linesize 200 pagesize 200
whenever sqlerror continue
column partition_name format a16
column subpartition_name format a18
column high_value format a26
DROP TABLE ventas_hash PURGE;
DROP TABLE ventas_list PURGE;
DROP TABLE ventas_comp PURGE;

PROMPT ===== HASH por cliente: reparte parejo, pero ninguna consulta de JD pide "todas las ventas de un grupo de clientes" =====
CREATE TABLE ventas_hash PARTITION BY HASH (cliente_id) PARTITIONS 4 TABLESPACE ts_sgejd_ventas
AS SELECT id, cliente_id, fecha_venta, estado FROM ventas;
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS_HASH');
SELECT partition_name, partition_position, num_rows FROM user_tab_partitions WHERE table_name = 'VENTAS_HASH' ORDER BY 2;

PROMPT ===== LIST por estado: aísla las anuladas (3 %) =====
CREATE TABLE ventas_list PARTITION BY LIST (estado) (
    PARTITION p_registrada VALUES ('REGISTRADA'),
    PARTITION p_anulada    VALUES ('ANULADA'),
    PARTITION p_otros      VALUES (DEFAULT)) TABLESPACE ts_sgejd_ventas
AS SELECT id, cliente_id, fecha_venta, estado FROM ventas;
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS_LIST');
SELECT partition_name, num_rows, high_value FROM user_tab_partitions WHERE table_name = 'VENTAS_LIST' ORDER BY partition_position;

PROMPT ===== COMPOSITE: RANGO por año y LIST por estado dentro de cada año =====
CREATE TABLE ventas_comp
PARTITION BY RANGE (fecha_venta) SUBPARTITION BY LIST (estado)
SUBPARTITION TEMPLATE (
    SUBPARTITION sp_registrada VALUES ('REGISTRADA'),
    SUBPARTITION sp_anulada    VALUES ('ANULADA'),
    SUBPARTITION sp_otros      VALUES (DEFAULT)) (
    PARTITION p_2024 VALUES LESS THAN (DATE '2025-01-01'),
    PARTITION p_2025 VALUES LESS THAN (DATE '2026-01-01'),
    PARTITION p_2026 VALUES LESS THAN (DATE '2027-01-01')) TABLESPACE ts_sgejd_ventas
AS SELECT id, cliente_id, fecha_venta, estado FROM ventas;
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'VENTAS_COMP', granularity => 'ALL');
SELECT partition_name, subpartition_name, num_rows FROM user_tab_subpartitions WHERE table_name = 'VENTAS_COMP' ORDER BY 1, 2;

PROMPT ===== ¿Sirve el pruning doble? "anuladas de 2025" =====
EXPLAIN PLAN FOR SELECT COUNT(*) FROM ventas_comp
WHERE fecha_venta >= DATE '2025-01-01' AND fecha_venta < DATE '2026-01-01' AND estado = 'ANULADA';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, NULL, 'BASIC +PARTITION +ROWS +COST'));
DELETE FROM plan_table;
COMMIT;

PROMPT ===== Las cuatro estrategias en el diccionario =====
column table_name format a14
SELECT table_name, partitioning_type, subpartitioning_type, partition_count, interval FROM user_part_tables
WHERE table_name IN ('VENTAS_PART', 'VENTAS_HASH', 'VENTAS_LIST', 'VENTAS_COMP') ORDER BY 1;
exit
