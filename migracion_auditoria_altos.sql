-- ============================================================
-- Migración: correcciones A1–A6 de la auditoría (15/10/2026)
-- Hibernate crea un CHECK con los estados de cada enum y ddl-auto=update NO lo actualiza.
-- Sin este script, convertir una proforma en venta falla (ORA-02290) porque el CHECK
-- de PROFORMAS.estado no admite el estado nuevo CONVERTIDA.
--
-- Ejecutar UNA vez, DESPUÉS de arrancar el backend nuevo al menos una vez
-- (así ya existen la columna PROFORMAS.venta_id y las tablas COBROS / DETALLE_COBRO):
--   Get-Content migracion_auditoria_altos.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces sin efectos duplicados.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

-- 1) Quitar el CHECK viejo de PROFORMAS.estado (el que no incluye CONVERTIDA)
BEGIN
    FOR c IN (SELECT constraint_name, search_condition_vc
                FROM user_constraints
               WHERE table_name = 'PROFORMAS' AND constraint_type = 'C') LOOP
        IF LOWER(c.search_condition_vc) LIKE 'estado in (%'
           AND c.search_condition_vc NOT LIKE '%CONVERTIDA%' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE PROFORMAS DROP CONSTRAINT ' || c.constraint_name;
            DBMS_OUTPUT.PUT_LINE('CHECK eliminado: ' || c.constraint_name);
        END IF;
    END LOOP;
END;
/

-- 2) CHECK nuevo con los 5 estados (si no existe ya)
DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe FROM user_constraints
     WHERE table_name = 'PROFORMAS' AND constraint_type = 'C'
       AND search_condition_vc LIKE '%CONVERTIDA%';
    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE PROFORMAS ADD CONSTRAINT CK_PROFORMAS_ESTADO
            CHECK (estado IN ('PENDIENTE','ACEPTADA','RECHAZADA','VENCIDA','CONVERTIDA'))]';
        DBMS_OUTPUT.PUT_LINE('CHECK CK_PROFORMAS_ESTADO creado');
    END IF;
END;
/

-- 3) Proformas que ya tenían venta: pasan a CONVERTIDA y guardan el N° de venta
UPDATE PROFORMAS p
   SET p.estado = 'CONVERTIDA',
       p.venta_id = (SELECT MIN(v.id) FROM VENTAS v WHERE v.proforma_id = p.id)
 WHERE EXISTS (SELECT 1 FROM VENTAS v WHERE v.proforma_id = p.id)
   AND (p.estado <> 'CONVERTIDA' OR p.venta_id IS NULL);

COMMIT;
