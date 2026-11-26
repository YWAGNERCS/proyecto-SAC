-- Distribución del esquema del negocio. Ejecutar como SYSDBA con respaldo previo.
-- Repetible: también completa un traslado que haya quedado interrumpido.
whenever sqlerror exit failure
set serveroutput on
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION;

DECLARE
    ruta VARCHAR2(1000);
    cantidad NUMBER;
    destino VARCHAR2(30);
    PROCEDURE ejecutar(sentencia VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE sentencia;
        DBMS_OUTPUT.PUT_LINE(sentencia);
    END;
    FUNCTION tablespace_de(tabla VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        IF tabla IN ('VENTAS', 'DETALLE_VENTA') THEN RETURN 'TS_SGEJD_VENTAS'; END IF;
        IF tabla IN ('VENTA_AUDITORIA', 'HISTORIAL_CAMBIOS', 'LOG_ERRORES') THEN
            RETURN 'TS_SGEJD_AUDITORIA';
        END IF;
        RETURN 'TS_SGEJD_DATOS';
    END;
BEGIN
    SELECT SUBSTR(file_name, 1, INSTR(file_name, '/', -1)) INTO ruta
    FROM dba_data_files WHERE tablespace_name = 'USERS' AND ROWNUM = 1;
    FOR nombre IN (SELECT 'TS_SGEJD_DATOS' nombre FROM dual UNION ALL
                   SELECT 'TS_SGEJD_VENTAS' FROM dual UNION ALL
                   SELECT 'TS_SGEJD_AUDITORIA' FROM dual) LOOP
        SELECT COUNT(*) INTO cantidad FROM dba_tablespaces WHERE tablespace_name = nombre.nombre;
        IF cantidad = 0 THEN
            ejecutar('CREATE TABLESPACE ' || nombre.nombre || ' DATAFILE ''' ||
                REPLACE(ruta, '''', '''''') || LOWER(nombre.nombre) || '01.dbf'' ' ||
                'SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 2G');
        END IF;
        ejecutar('ALTER USER JDREFRIGERACION QUOTA UNLIMITED ON ' || nombre.nombre);
    END LOOP;
    ejecutar('ALTER USER JDREFRIGERACION DEFAULT TABLESPACE TS_SGEJD_DATOS');

    FOR tabla IN (SELECT table_name, tablespace_name, partitioned FROM dba_tables
                  WHERE owner = 'JDREFRIGERACION' AND temporary = 'N' AND nested = 'NO'
                    AND secondary = 'N' AND iot_type IS NULL ORDER BY table_name) LOOP
        destino := tablespace_de(tabla.table_name);
        IF tabla.partitioned = 'YES' THEN
            ejecutar('ALTER TABLE JDREFRIGERACION."' || tabla.table_name ||
                     '" MODIFY DEFAULT ATTRIBUTES TABLESPACE ' || destino);
            FOR particion IN (SELECT partition_name FROM dba_tab_partitions
                              WHERE table_owner = 'JDREFRIGERACION' AND table_name = tabla.table_name
                                AND tablespace_name <> destino) LOOP
                ejecutar('ALTER TABLE JDREFRIGERACION."' || tabla.table_name || '" MOVE PARTITION "' ||
                         particion.partition_name || '" TABLESPACE ' || destino || ' UPDATE INDEXES');
            END LOOP;
        ELSIF tabla.tablespace_name <> destino THEN
            ejecutar('ALTER TABLE JDREFRIGERACION."' || tabla.table_name ||
                     '" MOVE ONLINE TABLESPACE ' || destino);
        END IF;
    END LOOP;

    -- Los LOB y sus índices internos tienen almacenamiento independiente de la cabecera.
    FOR lob IN (SELECT table_name, column_name, tablespace_name FROM dba_lobs
                WHERE owner = 'JDREFRIGERACION' AND partitioned = 'NO') LOOP
        destino := tablespace_de(lob.table_name);
        IF lob.tablespace_name <> destino THEN
            ejecutar('ALTER TABLE JDREFRIGERACION."' || lob.table_name || '" MOVE LOB ("' ||
                     lob.column_name || '") STORE AS (TABLESPACE ' || destino || ')');
        END IF;
    END LOOP;

    FOR indice IN (SELECT index_name, table_name, partitioned, tablespace_name, status FROM dba_indexes
                   WHERE owner = 'JDREFRIGERACION' AND index_type NOT LIKE 'LOB%' ORDER BY index_name) LOOP
        destino := tablespace_de(indice.table_name);
        IF indice.partitioned = 'YES' THEN
            ejecutar('ALTER INDEX JDREFRIGERACION."' || indice.index_name ||
                     '" MODIFY DEFAULT ATTRIBUTES TABLESPACE ' || destino);
            FOR particion IN (SELECT partition_name FROM dba_ind_partitions
                              WHERE index_owner = 'JDREFRIGERACION' AND index_name = indice.index_name
                                AND (tablespace_name <> destino OR status = 'UNUSABLE')) LOOP
                ejecutar('ALTER INDEX JDREFRIGERACION."' || indice.index_name || '" REBUILD PARTITION "' ||
                         particion.partition_name || '" TABLESPACE ' || destino);
            END LOOP;
        ELSIF indice.tablespace_name <> destino OR indice.status = 'UNUSABLE' THEN
            ejecutar('ALTER INDEX JDREFRIGERACION."' || indice.index_name || '" REBUILD TABLESPACE ' || destino);
        END IF;
    END LOOP;
END;
/
-- MOVE puede invalidar procedimientos que dependen de las tablas trasladadas.
BEGIN
    DBMS_UTILITY.COMPILE_SCHEMA(schema => 'JDREFRIGERACION', compile_all => FALSE);
END;
/
DECLARE
    cantidad NUMBER;
BEGIN
    SELECT COUNT(*) INTO cantidad FROM dba_objects
    WHERE owner = 'JDREFRIGERACION' AND status = 'INVALID';
    IF cantidad > 0 THEN RAISE_APPLICATION_ERROR(-20001, 'Quedaron objetos inválidos; revisar DBA_ERRORS.'); END IF;
    SELECT COUNT(*) INTO cantidad FROM dba_indexes
    WHERE owner = 'JDREFRIGERACION' AND status = 'UNUSABLE';
    IF cantidad > 0 THEN RAISE_APPLICATION_ERROR(-20002, 'Quedaron índices inutilizables.'); END IF;
    SELECT COUNT(*) INTO cantidad FROM dba_ind_partitions
    WHERE index_owner = 'JDREFRIGERACION' AND status = 'UNUSABLE';
    IF cantidad > 0 THEN RAISE_APPLICATION_ERROR(-20003, 'Quedaron particiones de índices inutilizables.'); END IF;
    SELECT COUNT(*) INTO cantidad FROM dba_segments
    WHERE owner = 'JDREFRIGERACION'
      AND tablespace_name NOT IN ('TS_SGEJD_DATOS', 'TS_SGEJD_VENTAS', 'TS_SGEJD_AUDITORIA');
    IF cantidad > 0 THEN RAISE_APPLICATION_ERROR(-20004, 'Quedaron segmentos fuera de los tres tablespaces.'); END IF;
END;
/
column tablespace_name format a25
SELECT tablespace_name, segment_type, COUNT(*) segmentos FROM dba_segments
WHERE owner = 'JDREFRIGERACION' GROUP BY tablespace_name, segment_type ORDER BY 1, 2;
SELECT COUNT(*) indices_inutilizables FROM dba_indexes
WHERE owner = 'JDREFRIGERACION' AND status = 'UNUSABLE';
exit
