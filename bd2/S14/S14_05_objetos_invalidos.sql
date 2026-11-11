-- BD2 · S14 · Paso 5: objetos que no están VALID, en la raíz (CDB$ROOT) y en FREEPDB1. Como SYSDBA.
-- DBA_OBJECTS es diccionario: sobrevive al reinicio, al revés que V$.
set echo off feedback on linesize 220 pagesize 200
column owner format a14
column object_name format a32
column object_type format a14
column con format a10
SELECT c.name AS con, o.owner, o.object_name, o.object_type, o.status
FROM cdb_objects o JOIN v$containers c ON c.con_id = o.con_id
WHERE o.status <> 'VALID' ORDER BY 1, 2, 3;
PROMPT ===== Componentes del diccionario (DBA_REGISTRY) que no están VALID =====
column comp_id format a10
column comp_name format a40
column version format a14
SELECT c.name AS con, r.comp_id, r.comp_name, r.version, r.status
FROM cdb_registry r JOIN v$containers c ON c.con_id = r.con_id WHERE r.status <> 'VALID' ORDER BY 1, 2;
PROMPT ===== Objetos del proyecto (JDREFRIGERACION) =====
SELECT object_type, status, COUNT(*) AS objetos FROM cdb_objects WHERE owner = 'JDREFRIGERACION' GROUP BY object_type, status ORDER BY 1;
exit
