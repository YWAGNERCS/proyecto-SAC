-- ============================================================
-- Migración: servicios técnicos de JD Refrigeración (28/10/2026)
--   1. Detracción (SPOT) y retención del IGV: sector SERVICIO en PRODUCTOS, agente de retención en
--      CLIENTES, deducción de cada venta en VENTAS y métodos DETRACCION/RETENCION en COBROS.
--   2. Sedes de los clientes (SEDES_CLIENTE).
--   3. Equipos instalados con serie y garantía (EQUIPOS_INSTALADOS).
--   4. Contratos de mantenimiento (CONTRATOS_MANTENIMIENTO) y órdenes de trabajo con sus materiales
--      (ORDENES_TRABAJO, MATERIALES_ORDEN).
--   5. MANTENIMIENTOS: cliente propio (se copia de su venta), equipo, contrato, técnico y orden;
--      la venta pasa a ser opcional (las visitas de un equipo o contrato no vienen de una venta).
--
-- Las tablas se crean aquí, y no por Hibernate, para que sus id sean GENERATED ALWAYS como los de
-- las demás tablas y para dejar sus reglas en la base.
--
-- Ejecutar UNA vez, con el backend detenido:
--   Get-Content migracion_servicios.sql | docker exec -i SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- Se puede ejecutar varias veces: lo que ya existe se salta.
-- ============================================================
SET FEEDBACK ON
SET SERVEROUTPUT ON

DECLARE
    v_existe NUMBER;

    FUNCTION existe_tabla(p_tabla VARCHAR2) RETURN BOOLEAN IS
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM all_tables WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = UPPER(p_tabla);
        RETURN v_existe > 0;
    END;

    FUNCTION existe_columna(p_tabla VARCHAR2, p_columna VARCHAR2) RETURN BOOLEAN IS
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM all_tab_columns
         WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = UPPER(p_tabla) AND column_name = UPPER(p_columna);
        RETURN v_existe > 0;
    END;

    PROCEDURE ejecutar(p_sql VARCHAR2, p_mensaje VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE(p_mensaje);
    END;

    PROCEDURE agregar_columna(p_tabla VARCHAR2, p_columna VARCHAR2, p_definicion VARCHAR2) IS
    BEGIN
        IF existe_columna(p_tabla, p_columna) THEN
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_columna || ': ya existe');
        ELSE
            ejecutar('ALTER TABLE ' || p_tabla || ' ADD (' || p_columna || ' ' || p_definicion || ')',
                     p_tabla || '.' || p_columna || ': agregada');
        END IF;
    END;

    -- Reemplaza el CHECK de una lista de valores (Hibernate lo creó con nombre SYS_C...) por uno con nombre propio
    PROCEDURE reemplazar_check(p_tabla VARCHAR2, p_columna VARCHAR2, p_nombre VARCHAR2, p_valores VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM all_constraints WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND constraint_name = UPPER(p_nombre);
        IF v_existe > 0 THEN
            DBMS_OUTPUT.PUT_LINE(p_nombre || ': ya existe');
            RETURN;
        END IF;
        FOR c IN (SELECT constraint_name FROM all_constraints
                   WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = UPPER(p_tabla) AND constraint_type = 'C'
                     AND LOWER(search_condition_vc) LIKE LOWER(p_columna) || ' in (%') LOOP
            ejecutar('ALTER TABLE ' || p_tabla || ' DROP CONSTRAINT ' || c.constraint_name,
                     p_tabla || ': se quitó ' || c.constraint_name);
        END LOOP;
        ejecutar('ALTER TABLE ' || p_tabla || ' ADD CONSTRAINT ' || p_nombre || ' CHECK (' || p_columna || ' IN (' || p_valores || '))',
                 p_nombre || ': creada');
    END;
BEGIN
    -- ── 1. Detracción y retención ──────────────────────────────
    reemplazar_check('productos', 'sector', 'ck_producto_sector',
        q'['CLIMATIZACION', 'REFRIGERACION', 'VENTILACION_FORZADA', 'MATERIAL_SERVICIO', 'SERVICIO']');
    agregar_columna('clientes', 'agente_retencion', 'BOOLEAN DEFAULT FALSE NOT NULL');
    agregar_columna('ventas', 'tipo_deduccion',
        q'[VARCHAR2(12) DEFAULT 'NINGUNA' NOT NULL CONSTRAINT ck_venta_deduccion CHECK (tipo_deduccion IN ('NINGUNA', 'DETRACCION', 'RETENCION'))]');
    agregar_columna('ventas', 'porcentaje_deduccion', 'NUMBER(5, 2)');
    agregar_columna('ventas', 'monto_deduccion', 'NUMBER(12, 2)');
    reemplazar_check('cobros', 'metodo_pago', 'ck_cobro_metodo',
        q'['EFECTIVO', 'TRANSFERENCIA', 'DEPOSITO', 'YAPE_PLIN', 'CHEQUE', 'DETRACCION', 'RETENCION']');

    -- ── 2. Sedes ───────────────────────────────────────────────
    IF NOT existe_tabla('sedes_cliente') THEN
        ejecutar(q'[
            CREATE TABLE sedes_cliente (
                id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                cliente_id NUMBER        NOT NULL CONSTRAINT fk_sede_cliente REFERENCES clientes (id),
                nombre     VARCHAR2(100) NOT NULL,
                direccion  VARCHAR2(200),
                contacto   VARCHAR2(100),
                telefono   VARCHAR2(20),
                CONSTRAINT uk_sede_cliente_nombre UNIQUE (cliente_id, nombre)
            )]', 'SEDES_CLIENTE: creada');
    END IF;

    -- ── 3. Órdenes de trabajo (antes que los equipos, que la referencian) ──
    IF NOT existe_tabla('ordenes_trabajo') THEN
        ejecutar(q'[
            CREATE TABLE ordenes_trabajo (
                id                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                tipo               VARCHAR2(15)  NOT NULL CONSTRAINT ck_orden_tipo
                                   CHECK (tipo IN ('INSTALACION', 'MANTENIMIENTO', 'REPARACION', 'VISITA_TECNICA')),
                estado             VARCHAR2(12)  NOT NULL CONSTRAINT ck_orden_estado
                                   CHECK (estado IN ('PROGRAMADA', 'REALIZADA', 'CANCELADA')),
                cliente_id         NUMBER        NOT NULL CONSTRAINT fk_orden_cliente REFERENCES clientes (id),
                sede_id            NUMBER        CONSTRAINT fk_orden_sede REFERENCES sedes_cliente (id),
                venta_id           NUMBER        CONSTRAINT fk_orden_venta REFERENCES ventas (id),
                mantenimiento_id   NUMBER        CONSTRAINT fk_orden_mantenimiento REFERENCES mantenimientos (id),
                equipo_id          NUMBER,
                fecha_programada   DATE          NOT NULL,
                tecnico            VARCHAR2(100) NOT NULL,
                direccion          VARCHAR2(200),
                descripcion        VARCHAR2(500) NOT NULL,
                fecha_realizada    DATE,
                trabajo_realizado  VARCHAR2(1000),
                recibido_por       VARCHAR2(100),
                observaciones      VARCHAR2(500),
                venta_facturada_id NUMBER        CONSTRAINT fk_orden_venta_facturada REFERENCES ventas (id),
                registrado_por     VARCHAR2(100),
                cerrado_por        VARCHAR2(100),
                motivo_cancelacion VARCHAR2(300),
                CONSTRAINT ck_orden_realizada CHECK (
                    estado <> 'REALIZADA' OR (fecha_realizada IS NOT NULL AND trabajo_realizado IS NOT NULL AND recibido_por IS NOT NULL)),
                CONSTRAINT ck_orden_cancelada CHECK (estado <> 'CANCELADA' OR motivo_cancelacion IS NOT NULL)
            )]', 'ORDENES_TRABAJO: creada');
    END IF;

    IF NOT existe_tabla('materiales_orden') THEN
        ejecutar(q'[
            CREATE TABLE materiales_orden (
                id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                orden_id       NUMBER        NOT NULL CONSTRAINT fk_material_orden REFERENCES ordenes_trabajo (id),
                producto_id    NUMBER        NOT NULL CONSTRAINT fk_material_producto REFERENCES productos (id),
                cantidad       NUMBER(10)    NOT NULL CONSTRAINT ck_material_cantidad CHECK (cantidad > 0),
                costo_unitario NUMBER(12, 2) NOT NULL,
                moneda         VARCHAR2(10)  NOT NULL CONSTRAINT ck_material_moneda CHECK (moneda IN ('SOLES', 'DOLARES')),
                CONSTRAINT uk_material_orden_producto UNIQUE (orden_id, producto_id)
            )]', 'MATERIALES_ORDEN: creada');
    END IF;

    -- ── 4. Equipos instalados ──────────────────────────────────
    IF NOT existe_tabla('equipos_instalados') THEN
        ejecutar(q'[
            CREATE TABLE equipos_instalados (
                id                NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                cliente_id        NUMBER        NOT NULL CONSTRAINT fk_equipo_cliente REFERENCES clientes (id),
                sede_id           NUMBER        CONSTRAINT fk_equipo_sede REFERENCES sedes_cliente (id),
                producto_id       NUMBER        NOT NULL CONSTRAINT fk_equipo_producto REFERENCES productos (id),
                numero_serie      VARCHAR2(60),
                ubicacion         VARCHAR2(100),
                fecha_instalacion DATE          NOT NULL,
                meses_garantia    NUMBER(3)     DEFAULT 12 NOT NULL CONSTRAINT ck_equipo_garantia CHECK (meses_garantia BETWEEN 0 AND 120),
                frecuencia_meses  NUMBER(2)     CONSTRAINT ck_equipo_frecuencia CHECK (frecuencia_meses IN (1, 2, 3, 4, 6, 12)),
                estado            VARCHAR2(10)  NOT NULL CONSTRAINT ck_equipo_estado CHECK (estado IN ('ACTIVO', 'RETIRADO')),
                venta_id          NUMBER        CONSTRAINT fk_equipo_venta REFERENCES ventas (id),
                orden_trabajo_id  NUMBER        CONSTRAINT fk_equipo_orden REFERENCES ordenes_trabajo (id),
                notas             VARCHAR2(300)
            )]', 'EQUIPOS_INSTALADOS: creada');
        ejecutar('ALTER TABLE ordenes_trabajo ADD CONSTRAINT fk_orden_equipo FOREIGN KEY (equipo_id) REFERENCES equipos_instalados (id)',
                 'FK_ORDEN_EQUIPO: creada');
    END IF;

    IF NOT existe_tabla('contratos_mantenimiento') THEN
        ejecutar(q'[
            CREATE TABLE contratos_mantenimiento (
                id                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                cliente_id         NUMBER        NOT NULL CONSTRAINT fk_contrato_cliente REFERENCES clientes (id),
                sede_id            NUMBER        CONSTRAINT fk_contrato_sede REFERENCES sedes_cliente (id),
                fecha_inicio       DATE          NOT NULL,
                fecha_fin          DATE          NOT NULL,
                frecuencia_meses   NUMBER(2)     NOT NULL CONSTRAINT ck_contrato_frecuencia CHECK (frecuencia_meses IN (1, 2, 3, 4, 6, 12)),
                monto_visita       NUMBER(12, 2) NOT NULL CONSTRAINT ck_contrato_monto CHECK (monto_visita > 0),
                moneda             VARCHAR2(10)  NOT NULL CONSTRAINT ck_contrato_moneda CHECK (moneda IN ('SOLES', 'DOLARES')),
                descripcion        VARCHAR2(300) NOT NULL,
                estado             VARCHAR2(12)  NOT NULL CONSTRAINT ck_contrato_estado CHECK (estado IN ('VIGENTE', 'CANCELADO')),
                registrado_por     VARCHAR2(100),
                motivo_cancelacion VARCHAR2(300),
                CONSTRAINT ck_contrato_fechas CHECK (fecha_fin > fecha_inicio)
            )]', 'CONTRATOS_MANTENIMIENTO: creada');
    END IF;

    -- ── 5. Mantenimientos ─────────────────────────────────────
    IF NOT existe_columna('mantenimientos', 'cliente_id') THEN
        ejecutar('ALTER TABLE mantenimientos ADD (cliente_id NUMBER)', 'MANTENIMIENTOS.cliente_id: agregada');
        ejecutar('UPDATE mantenimientos m SET cliente_id = (SELECT v.cliente_id FROM ventas v WHERE v.id = m.venta_id)',
                 'MANTENIMIENTOS.cliente_id: copiada de su venta');
        ejecutar('ALTER TABLE mantenimientos MODIFY (cliente_id NOT NULL)', 'MANTENIMIENTOS.cliente_id: obligatoria');
        ejecutar('ALTER TABLE mantenimientos ADD CONSTRAINT fk_mantenimiento_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id)',
                 'FK_MANTENIMIENTO_CLIENTE: creada');
    ELSE
        DBMS_OUTPUT.PUT_LINE('MANTENIMIENTOS.cliente_id: ya existe');
    END IF;
    agregar_columna('mantenimientos', 'equipo_id', 'NUMBER CONSTRAINT fk_mantenimiento_equipo REFERENCES equipos_instalados (id)');
    agregar_columna('mantenimientos', 'contrato_id', 'NUMBER CONSTRAINT fk_mantenimiento_contrato REFERENCES contratos_mantenimiento (id)');
    agregar_columna('mantenimientos', 'tecnico', 'VARCHAR2(100)');
    agregar_columna('mantenimientos', 'orden_trabajo_id', 'NUMBER CONSTRAINT fk_mantenimiento_orden REFERENCES ordenes_trabajo (id)');

    SELECT COUNT(*) INTO v_existe FROM all_tab_columns
     WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND table_name = 'MANTENIMIENTOS' AND column_name = 'VENTA_ID' AND nullable = 'N';
    IF v_existe > 0 THEN
        ejecutar('ALTER TABLE mantenimientos MODIFY (venta_id NULL)', 'MANTENIMIENTOS.venta_id: ahora opcional');
    END IF;
    SELECT COUNT(*) INTO v_existe FROM all_constraints WHERE owner = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AND constraint_name = 'CK_MANTENIMIENTO_ORIGEN';
    IF v_existe = 0 THEN
        ejecutar(q'[ALTER TABLE mantenimientos ADD CONSTRAINT ck_mantenimiento_origen
                    CHECK (venta_id IS NOT NULL OR equipo_id IS NOT NULL OR contrato_id IS NOT NULL)]',
                 'CK_MANTENIMIENTO_ORIGEN: creada (cada visita viene de una venta, un equipo o un contrato)');
    END IF;
END;
/
