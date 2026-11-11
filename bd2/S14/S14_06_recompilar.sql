-- BD2 · S14 · Paso 6: diagnóstico e intento de recompilación del sinónimo inválido. Como SYSDBA, en FREEPDB1.
set echo on feedback on linesize 200 pagesize 100
ALTER SESSION SET CONTAINER = FREEPDB1;
column table_owner format a12
column table_name format a26
column owner format a10
column object_name format a26
-- ¿A qué apunta el sinónimo y existe ese objeto?
SELECT owner, synonym_name, table_owner, table_name FROM dba_synonyms WHERE synonym_name = 'SDO_AGGR_POINTAPPEND';
SELECT owner, object_name, object_type, status FROM dba_objects WHERE object_name = 'SDO_AGGR_POINTAPPEND' AND owner <> 'PUBLIC';
SELECT COUNT(*) AS objetos_mdsys FROM dba_objects WHERE owner = 'MDSYS';
SELECT comp_id, status FROM dba_registry WHERE comp_id IN ('SDO', 'SPATIAL', 'ORDIM');
-- ¿Quién depende de él?
SELECT COUNT(*) AS dependientes FROM dba_dependencies WHERE referenced_name = 'SDO_AGGR_POINTAPPEND';
-- Intento real de recompilación
ALTER PUBLIC SYNONYM sdo_aggr_pointappend COMPILE;
SELECT owner, object_name, object_type, status FROM dba_objects WHERE object_name = 'SDO_AGGR_POINTAPPEND' AND owner = 'PUBLIC';
exit
