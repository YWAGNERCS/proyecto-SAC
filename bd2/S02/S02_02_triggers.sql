-- ============================================================
-- BD2 · S2 · Paso 2: los dos triggers sobre VENTAS. Se ejecuta como JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S02\S02_02_triggers.sql
--
-- Regla (BEFORE): una venta anulada es definitiva, y anular exige motivo y quién. Compara :OLD con :NEW,
-- por eso no puede ser una CHECK: una CHECK solo ve la fila nueva, no sabe de dónde viene.
-- Coincide con VentaService.anular() (motivo de 5 a 300 caracteres; "ya está ANULADA" = 409).
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue

CREATE OR REPLACE TRIGGER trg_venta_estado_bu
BEFORE UPDATE OF estado ON ventas
FOR EACH ROW
BEGIN
    -- 1) Una venta ANULADA no vuelve a REGISTRADA: su stock ya se devolvió y su nota de crédito ya existe
    IF :OLD.estado = 'ANULADA' AND :NEW.estado <> 'ANULADA' THEN
        RAISE_APPLICATION_ERROR(-20021,
            'La venta ' || :OLD.id || ' está ANULADA y no puede volver a ' || :NEW.estado || '.');
    END IF;

    -- 2) Al pasar a ANULADA: motivo de al menos 5 caracteres y quién la anuló
    IF :OLD.estado = 'REGISTRADA' AND :NEW.estado = 'ANULADA' THEN
        IF LENGTH(TRIM(:NEW.motivo_anulacion)) IS NULL OR LENGTH(TRIM(:NEW.motivo_anulacion)) < 5 THEN
            RAISE_APPLICATION_ERROR(-20022,
                'Para anular la venta ' || :OLD.id || ' indique el motivo (al menos 5 caracteres).');
        END IF;
        IF :NEW.anulado_por IS NULL THEN
            RAISE_APPLICATION_ERROR(-20023, 'Para anular la venta ' || :OLD.id || ' indique quién la anula.');
        END IF;
        -- 3) La fecha la pone la base si quien actualiza no la envió
        :NEW.fecha_anulacion := NVL(:NEW.fecha_anulacion, TRUNC(SYSDATE));
    END IF;
END;
/
SHOW ERRORS

-- Auditoría (AFTER): una fila por cada INSERT, UPDATE o DELETE de una venta
CREATE OR REPLACE TRIGGER trg_venta_auditoria
AFTER INSERT OR UPDATE OR DELETE ON ventas
FOR EACH ROW
DECLARE
    v_accion venta_auditoria.accion%TYPE;
BEGIN
    IF INSERTING THEN
        v_accion := 'INSERT';
    ELSIF UPDATING THEN
        v_accion := 'UPDATE';
    ELSE
        v_accion := 'DELETE';
    END IF;

    INSERT INTO venta_auditoria (id_venta, accion, estado_anterior, estado_nuevo,
                                 estado_pago_anterior, estado_pago_nuevo, cliente_id,
                                 anulado_por, motivo, programa, usuario_so)
    VALUES (COALESCE(:NEW.id, :OLD.id), v_accion, :OLD.estado, :NEW.estado,
            :OLD.estado_pago, :NEW.estado_pago, COALESCE(:NEW.cliente_id, :OLD.cliente_id),
            COALESCE(:NEW.anulado_por, :OLD.anulado_por), COALESCE(:NEW.motivo_anulacion, :OLD.motivo_anulacion),
            SUBSTR(SYS_CONTEXT('USERENV', 'MODULE'), 1, 64), SYS_CONTEXT('USERENV', 'OS_USER'));
END;
/
SHOW ERRORS

SELECT trigger_name, trigger_type, triggering_event, status
FROM user_triggers
WHERE table_name = 'VENTAS'
ORDER BY trigger_name;
exit
