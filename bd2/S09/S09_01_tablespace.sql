-- ============================================================
-- BD2 · S9 · Almacenamiento, seguridad y auditoría · SGE-JD
-- Paso 1: tablespace propio para las ventas, mover las tablas y reconstruir sus índices.
-- Como SYSTEM en FREEPDB1:   .\bd2\herramientas\sql.ps1 bd2\S09\S09_01_tablespace.sql -Dba
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue
column tablespace_name format a16
column file_name format a60
column index_name format a26
column table_name format a14
column segment_name format a26

-- Ruta explícita dentro de /opt/oracle/oradata: es el único directorio que compose-bd2.yml monta como volumen.
-- Sin ruta, con db_create_file_dest vacío, el datafile caería fuera del volumen y se perdería al recrear el contenedor.
CREATE TABLESPACE ts_sgejd_ventas
  DATAFILE '/opt/oracle/oradata/FREE/FREEPDB1/ts_sgejd_ventas01.dbf' SIZE 100M
  AUTOEXTEND ON NEXT 50M MAXSIZE 2G;

SELECT tablespace_name, file_name, ROUND(bytes / 1024 / 1024) AS mb, autoextensible, ROUND(maxbytes / 1024 / 1024) AS max_mb
FROM dba_data_files WHERE tablespace_name = 'TS_SGEJD_VENTAS';

-- 1) Mover ANTES de dar cuota: el dueño no puede tener segmentos en un tablespace sin cuota
ALTER TABLE jdrefrigeracion.ventas MOVE TABLESPACE ts_sgejd_ventas;

-- 2) Cuota para el dueño (JD_APP no la necesita: no crea segmentos, solo escribe filas en los del dueño)
ALTER USER jdrefrigeracion QUOTA UNLIMITED ON ts_sgejd_ventas;
ALTER TABLE jdrefrigeracion.ventas        MOVE TABLESPACE ts_sgejd_ventas;
ALTER TABLE jdrefrigeracion.detalle_venta MOVE TABLESPACE ts_sgejd_ventas;

-- 3) Qué les pasó a los índices
SELECT table_name, index_name, status, tablespace_name FROM dba_indexes
WHERE owner = 'JDREFRIGERACION' AND table_name IN ('VENTAS', 'DETALLE_VENTA') ORDER BY table_name, index_name;

-- 4) Lo que vería la aplicación con el índice de la PK en UNUSABLE
SELECT /*+ INDEX(v) */ id, fecha_venta FROM jdrefrigeracion.ventas v WHERE id = 1;
INSERT INTO jdrefrigeracion.ventas (cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion)
VALUES ((SELECT MIN(id) FROM jdrefrigeracion.clientes), TRUNC(SYSDATE), 'SOLES', 'CONTADO', 'PAGADO', 'REGISTRADA', 'NINGUNA');
ROLLBACK;

-- 5) Reconstruir cada índice, y llevarlo al tablespace nuevo (REBUILD sin TABLESPACE lo deja donde estaba)
BEGIN
    FOR i IN (SELECT index_name FROM dba_indexes
              WHERE owner = 'JDREFRIGERACION' AND table_name IN ('VENTAS', 'DETALLE_VENTA') AND status = 'UNUSABLE') LOOP
        EXECUTE IMMEDIATE 'ALTER INDEX jdrefrigeracion.' || i.index_name || ' REBUILD TABLESPACE ts_sgejd_ventas';
    END LOOP;
END;
/

SELECT table_name, index_name, status, tablespace_name FROM dba_indexes
WHERE owner = 'JDREFRIGERACION' AND table_name IN ('VENTAS', 'DETALLE_VENTA') ORDER BY table_name, index_name;
SELECT segment_name, segment_type, tablespace_name, extents, ROUND(bytes / 1024) AS kb FROM dba_segments
WHERE owner = 'JDREFRIGERACION' AND tablespace_name = 'TS_SGEJD_VENTAS' ORDER BY segment_type DESC, segment_name;

-- 6) La misma consulta que antes falló, ahora
SELECT /*+ INDEX(v) */ id, fecha_venta FROM jdrefrigeracion.ventas v WHERE id = 1;
exit
