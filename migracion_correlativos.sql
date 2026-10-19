-- ============================================================
-- Migración: correlativos de comprobantes (15/10/2026)
-- Antes, el número de cada factura/boleta se elegía al azar (Math.random()).
-- Este script los RENUMERA por serie en el orden en que se emitieron:
--   F001-00000001, F001-00000002, ... · B001-00000001, ... · FC01/BC01 igual.
-- También corrige los enlaces PDF/XML que contienen el número.
--
-- Ejecutar UNA vez, con el backend detenido, ANTES de arrancar el código nuevo
-- (el backend nuevo crea la restricción única serie+número, que fallaría si hubiera repetidos):
--   Get-Content migracion_correlativos.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- ============================================================
SET FEEDBACK ON

MERGE INTO COMPROBANTES c
USING (SELECT id, LPAD(ROW_NUMBER() OVER (PARTITION BY serie ORDER BY id), 8, '0') AS nuevo FROM COMPROBANTES) x
ON (c.id = x.id)
WHEN MATCHED THEN UPDATE SET
    c.enlace_pdf = REPLACE(c.enlace_pdf, c.serie || '-' || c.numero, c.serie || '-' || x.nuevo),
    c.enlace_xml = REPLACE(c.enlace_xml, c.serie || '-' || c.numero, c.serie || '-' || x.nuevo),
    c.numero     = x.nuevo;

MERGE INTO NOTAS_CREDITO n
USING (SELECT id, LPAD(ROW_NUMBER() OVER (PARTITION BY serie ORDER BY id), 8, '0') AS nuevo FROM NOTAS_CREDITO) x
ON (n.id = x.id)
WHEN MATCHED THEN UPDATE SET
    n.enlace_pdf = REPLACE(n.enlace_pdf, n.serie || '-' || n.numero, n.serie || '-' || x.nuevo),
    n.enlace_xml = REPLACE(n.enlace_xml, n.serie || '-' || n.numero, n.serie || '-' || x.nuevo),
    n.numero     = x.nuevo;

-- Los contadores se recalculan solos desde lo emitido (si la tabla aún no existe, no pasa nada)
BEGIN
    EXECUTE IMMEDIATE 'DELETE FROM SERIES_COMPROBANTE';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

COMMIT;
