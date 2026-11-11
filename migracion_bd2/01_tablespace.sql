-- ============================================================
-- Migración BD2 · 1/7 · Tablespace propio de ventas (BD2 S9)
-- VENTAS y DETALLE_VENTA pasan a TS_SGEJD_VENTAS, con sus índices. El datafile va dentro de
-- /opt/oracle/oradata, que es el volumen: fuera de ahí se perdería al recrear el contenedor.
-- MOVE ONLINE: la aplicación puede seguir leyendo y escribiendo, y los índices no quedan UNUSABLE
-- (en S9, con MOVE simple, la PK quedó UNUSABLE y el INSERT siguiente falló con ORA-01502).
-- Lo ejecuta migracion_bd2\migrar.ps1 como SYSDBA. Se puede repetir.
-- ============================================================
whenever sqlerror exit failure
set serveroutput on feedback off
ALTER SESSION SET CONTAINER = FREEPDB1;
-- otra vez: DBMS_OUTPUT se habilita por contenedor
set serveroutput on
DECLARE
    v_n NUMBER;
    v_ruta VARCHAR2(200);
    PROCEDURE ejecutar(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || SUBSTR(p_sql, 1, 110));
    END;
BEGIN
    SELECT COUNT(*) INTO v_n FROM dba_tablespaces WHERE tablespace_name = 'TS_SGEJD_VENTAS';
    IF v_n = 0 THEN
        -- Junto a los datafiles de FREEPDB1 (dentro del volumen)
        SELECT SUBSTR(file_name, 1, INSTR(file_name, '/', -1)) INTO v_ruta
        FROM dba_data_files WHERE tablespace_name = 'USERS' AND ROWNUM = 1;
        ejecutar('CREATE TABLESPACE ts_sgejd_ventas DATAFILE ''' || v_ruta || 'ts_sgejd_ventas01.dbf'' '
              || 'SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 2G');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  TS_SGEJD_VENTAS ya existe');
    END IF;
    ejecutar('ALTER USER jdrefrigeracion QUOTA UNLIMITED ON ts_sgejd_ventas');

    FOR t IN (SELECT table_name FROM dba_tables
              WHERE owner = 'JDREFRIGERACION' AND table_name IN ('VENTAS', 'DETALLE_VENTA')
                AND tablespace_name <> 'TS_SGEJD_VENTAS') LOOP
        BEGIN
            ejecutar('ALTER TABLE jdrefrigeracion.' || t.table_name || ' MOVE ONLINE TABLESPACE ts_sgejd_ventas');
        EXCEPTION WHEN OTHERS THEN
            IF SQLCODE = -439 THEN   -- la edición no tiene "online table move": MOVE simple y se reconstruyen índices
                ejecutar('ALTER TABLE jdrefrigeracion.' || t.table_name || ' MOVE TABLESPACE ts_sgejd_ventas');
            ELSE
                RAISE;
            END IF;
        END;
    END LOOP;

    -- Los índices de esas dos tablas también al tablespace nuevo (y reconstruidos si quedaron UNUSABLE)
    FOR i IN (SELECT index_name FROM dba_indexes
              WHERE owner = 'JDREFRIGERACION' AND table_name IN ('VENTAS', 'DETALLE_VENTA')
                AND index_type NOT LIKE 'LOB%'
                AND (tablespace_name <> 'TS_SGEJD_VENTAS' OR status = 'UNUSABLE')) LOOP
        ejecutar('ALTER INDEX jdrefrigeracion.' || i.index_name || ' REBUILD TABLESPACE ts_sgejd_ventas');
    END LOOP;
END;
/
set feedback on
column segment_name format a30
column tablespace_name format a16
SELECT segment_name, segment_type, tablespace_name FROM dba_segments
WHERE owner = 'JDREFRIGERACION' AND tablespace_name = 'TS_SGEJD_VENTAS' ORDER BY segment_type DESC, segment_name;
SELECT COUNT(*) AS indices_unusable FROM dba_indexes WHERE owner = 'JDREFRIGERACION' AND status = 'UNUSABLE';
exit
