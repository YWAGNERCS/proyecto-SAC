-- ============================================================
-- Migración: tabla DEVOLUCIONES (auditoría A8, 25/10/2026)
-- Al anular una venta que el cliente ya pagó (al contado o con abonos), Cobranza deja una
-- devolución PENDIENTE por lo pagado; cuando JD devuelve el dinero se registra fecha, método
-- y N° de operación y pasa a DEVUELTA.
--
-- La tabla se crea aquí, y no por Hibernate, para que su id sea GENERATED ALWAYS como el de
-- las demás tablas y para dejar sus reglas en la base (una devolución por venta, monto > 0).
--
-- Ejecutar UNA vez, con el backend detenido:
--   Get-Content migracion_devoluciones.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces: si la tabla ya existe, no hace nada.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM all_tables
     WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = 'DEVOLUCIONES';

    IF v_existe > 0 THEN
        DBMS_OUTPUT.PUT_LINE('DEVOLUCIONES: ya existe');
        RETURN;
    END IF;

    EXECUTE IMMEDIATE q'[
        CREATE TABLE devoluciones (
            id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            venta_id         NUMBER        NOT NULL CONSTRAINT uk_devolucion_venta UNIQUE
                                           CONSTRAINT fk_devolucion_venta REFERENCES ventas (id),
            cliente_id       NUMBER        NOT NULL CONSTRAINT fk_devolucion_cliente REFERENCES clientes (id),
            moneda           VARCHAR2(10)  NOT NULL CONSTRAINT ck_devolucion_moneda CHECK (moneda IN ('SOLES', 'DOLARES')),
            monto            NUMBER(12, 2) NOT NULL CONSTRAINT ck_devolucion_monto CHECK (monto > 0),
            estado           VARCHAR2(15)  NOT NULL CONSTRAINT ck_devolucion_estado CHECK (estado IN ('PENDIENTE', 'DEVUELTA')),
            fecha_anulacion  DATE          NOT NULL,
            fecha_devolucion DATE,
            metodo_pago      VARCHAR2(20)  CONSTRAINT ck_devolucion_metodo
                                           CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'DEPOSITO', 'YAPE_PLIN', 'CHEQUE')),
            referencia       VARCHAR2(100),
            notas            VARCHAR2(300),
            CONSTRAINT ck_devolucion_devuelta CHECK (
                estado = 'PENDIENTE' OR (fecha_devolucion IS NOT NULL AND metodo_pago IS NOT NULL))
        )]';
    DBMS_OUTPUT.PUT_LINE('DEVOLUCIONES: creada');
END;
/
