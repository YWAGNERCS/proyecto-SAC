-- ============================================================
-- SGE-JD · Movimientos de demostración: 20 ventas por año (2024, 2025 y 2026)   (11/11/2026)
--
-- Se ejecuta DESPUÉS de datos_demo.sql (que deja la base vacía con el catálogo, los 8 clientes y los 7 proveedores
-- de demostración, con los ID desde 1). Lo más simple: scripts/datos_demo.bat (respalda y ejecuta los dos).
--
-- Cada venta es completa y coherente, con los mismos valores que deja el backend en modo simulado:
--   * 60 ventas (20 por año) desde 20 plantillas reales (equipos con instalación, gases, repuestos, ventilación),
--     en soles o en dólares según el producto, con el tipo de cambio guardado (como hace el backend);
--   * total = Σ líneas (precio × cantidad × (1 − descuento)) + 18 % de IGV, redondeado como Venta.calcularTotal();
--   * factura F001 ACEPTADA (ambiente SIMULADO, "Simulado: no se envió a SUNAT") con su envío en el historial;
--   * 2 de cada 3 al contado (PAGADO); las de crédito (30 días) se cobran con 1 o 2 abonos; en 2026 una queda
--     ATRASADA sin cobrar y otra con un abono parcial;
--   * 1 de cada 4 viene de una proforma CONVERTIDA (más una proforma PENDIENTE de noviembre de 2026);
--   * 1 anulada por año (a crédito, sin cobros) con su nota de crédito FC01 ACEPTADA;
--   * los equipos con rutina programan 2 visitas (a los 6 y 12 meses): las pasadas quedan REALIZADAS, las de los
--     últimos 20 días PENDIENTES (vencidas en la agenda) y las futuras PENDIENTES;
--   * el stock de los materiales lo dan 3 compras al contado cada enero (y una a crédito en octubre de 2026,
--     pendiente de pago), así el kárdex cuadra: stock = compras − ventas no anuladas.
-- Sin detracción ni retención: el catálogo de demostración no tiene mano de obra (sector SERVICIO) y ningún
-- cliente es agente de retención.
-- Al final se vacían VENTA_AUDITORIA y LOG_ERRORES (BD2): la carga no es actividad de usuarios.
--
-- Ejecutar como el dueño del esquema, o como SYSDBA con ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION.
-- ============================================================
SET DEFINE OFF
SET FEEDBACK OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DECLARE
    c_igv      CONSTANT NUMBER := 0.18;
    c_simulado CONSTANT VARCHAR2(40) := 'Simulado: no se envió a SUNAT';
    c_url      CONSTANT VARCHAR2(60) := 'https://www.nubefact.com/api/v1/demo/pdf/';
    v_hoy      DATE := TRUNC(SYSDATE);
    v_n_fact   PLS_INTEGER := 0;
    v_n_nota   PLS_INTEGER := 0;
    v_n_ventas PLS_INTEGER := 0;
    v_n_cobros PLS_INTEGER := 0;
    v_n_visit  PLS_INTEGER := 0;

    -- Plantilla: líneas "prefijo del nombre|cantidad|descuento %" separadas por ";", y si programa rutina
    TYPE t_plantilla IS RECORD (lineas VARCHAR2(400), rutina BOOLEAN);
    TYPE t_plantillas IS TABLE OF t_plantilla INDEX BY PLS_INTEGER;
    p t_plantillas;

    TYPE t_num IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    v_compra_anual t_num;   -- producto → cantidad a comprar cada enero

    FUNCTION producto(p_prefijo VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT id INTO v_id FROM productos WHERE nombre LIKE p_prefijo || '%';
        RETURN v_id;
    EXCEPTION WHEN NO_DATA_FOUND OR TOO_MANY_ROWS THEN
        RAISE_APPLICATION_ERROR(-20900, 'Producto de la plantilla no encontrado o ambiguo: ' || p_prefijo);
    END;

    FUNCTION parte(p_texto VARCHAR2, p_n PLS_INTEGER, p_sep VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        RETURN REGEXP_SUBSTR(p_texto, '[^' || p_sep || ']+', 1, p_n);
    END;

    FUNCTION precio(p_id NUMBER) RETURN NUMBER IS
        v NUMBER;
    BEGIN
        SELECT ROUND(costo_base * (1 + margen_porcentaje / 100), 2) INTO v FROM productos WHERE id = p_id;
        RETURN v;
    END;

    -- Una compra con IGV incluido (como CompraService): total, subtotal = total / 1.18 e IGV = la diferencia
    PROCEDURE compra(p_fecha DATE, p_proveedor VARCHAR2, p_factura VARCHAR2, p_productos VARCHAR2, p_credito BOOLEAN) IS
        v_compra NUMBER;
        v_prov   NUMBER;
        v_total  NUMBER := 0;
        v_id     NUMBER;
        v_cant   NUMBER;
        v_costo  NUMBER;
        i        PLS_INTEGER := 1;
    BEGIN
        SELECT id INTO v_prov FROM proveedores WHERE nombre_o_razon_social LIKE p_proveedor || '%';
        INSERT INTO compras (fecha_compra, moneda, numero_factura, total, proveedor_id, tipo_pago, dias_credito,
                             fecha_vencimiento, estado_pago, registrado_por, subtotal, igv)
        VALUES (p_fecha, 'SOLES', p_factura, 0, v_prov, CASE WHEN p_credito THEN 'CREDITO' ELSE 'CONTADO' END,
                CASE WHEN p_credito THEN 30 END, CASE WHEN p_credito THEN p_fecha + 30 END,
                CASE WHEN p_credito THEN 'PENDIENTE' ELSE 'PAGADA' END, 'lucy', 0, 0)
        RETURNING id INTO v_compra;
        LOOP
            EXIT WHEN parte(p_productos, i, ';') IS NULL;
            v_id := producto(parte(parte(p_productos, i, ';'), 1, '|'));
            v_cant := NVL(TO_NUMBER(parte(parte(p_productos, i, ';'), 2, '|')), v_compra_anual(v_id));
            SELECT costo_base INTO v_costo FROM productos WHERE id = v_id;
            INSERT INTO detalle_compra (cantidad, precio_unitario_compra, compra_id, producto_id)
            VALUES (v_cant, ROUND(v_costo * (1 + c_igv), 2), v_compra, v_id);
            v_total := v_total + v_cant * ROUND(v_costo * (1 + c_igv), 2);
            UPDATE productos SET ultimo_costo_compra = costo_base, fecha_ultima_compra = p_fecha WHERE id = v_id;
            i := i + 1;
        END LOOP;
        UPDATE compras SET total = ROUND(v_total, 2), subtotal = ROUND(v_total / (1 + c_igv), 2),
                           igv = ROUND(v_total, 2) - ROUND(v_total / (1 + c_igv), 2)
         WHERE id = v_compra;
    END;

    PROCEDURE venta(p_anio PLS_INTEGER, p_i PLS_INTEGER) IS
        v_fecha    DATE;
        v_cliente  clientes%ROWTYPE;
        v_moneda   VARCHAR2(10);
        v_tc       NUMBER;
        v_credito  BOOLEAN := MOD(p_i, 3) = 0 OR p_i = 17;
        v_anulada  BOOLEAN := p_i = 17;
        v_venta    NUMBER;
        v_prof     NUMBER;
        v_comp     NUMBER;
        v_sub      NUMBER := 0;
        v_total    NUMBER;
        v_id       NUMBER;
        v_cant     NUMBER;
        v_desc     NUMBER;
        v_precio   NUMBER;
        v_costo    NUMBER;
        v_nombre   productos.nombre%TYPE;
        v_numero   VARCHAR2(8);
        v_vendedor VARCHAR2(20) := CASE WHEN MOD(p_i, 5) = 0 THEN 'lisandro' ELSE 'lucy' END;
        v_lineas   VARCHAR2(400) := p(p_i).lineas;
        v_span     NUMBER := CASE p_anio WHEN 2026 THEN 285 ELSE 340 END;
        k          PLS_INTEGER := 1;
    BEGIN
        v_fecha := TO_DATE(p_anio || '-01-10', 'YYYY-MM-DD') + TRUNC((p_i - 1) * v_span / 19);
        SELECT * INTO v_cliente FROM clientes WHERE id = MOD(p_i * 3 + (p_anio - 2024) * 2, 8) + 1;
        v_id := producto(parte(parte(v_lineas, 1, ';'), 1, '|'));   -- (una función local no se puede usar en SQL)
        SELECT moneda INTO v_moneda FROM productos WHERE id = v_id;
        v_tc := CASE p_anio WHEN 2024 THEN 3.75 WHEN 2025 THEN 3.62 ELSE 3.47 END + MOD(p_i, 5) / 100;

        -- 1 de cada 4 viene de una proforma aceptada y convertida (no la anulada)
        IF MOD(p_i, 4) = 1 AND NOT v_anulada THEN
            INSERT INTO proformas (estado, fecha_emision, fecha_vencimiento, moneda, cliente_id, tipo_cambio,
                                   enviado_correo, enviado_whatsapp)
            VALUES ('CONVERTIDA', v_fecha - 7, v_fecha + 8, v_moneda, v_cliente.id, v_tc, TRUE, FALSE)
            RETURNING id INTO v_prof;
        END IF;

        INSERT INTO ventas (fecha_venta, moneda, tipo_cambio, tipo_pago, dias_credito, fecha_limite_pago, estado_pago,
                            estado, cliente_id, proforma_id, vendedor_nombre, tipo_deduccion)
        VALUES (v_fecha, v_moneda, v_tc, CASE WHEN v_credito THEN 'CREDITO' ELSE 'CONTADO' END,
                CASE WHEN v_credito THEN 30 END, CASE WHEN v_credito THEN v_fecha + 30 END,
                CASE WHEN v_credito THEN 'PENDIENTE' ELSE 'PAGADO' END, 'REGISTRADA', v_cliente.id, v_prof,
                v_vendedor, 'NINGUNA')
        RETURNING id INTO v_venta;
        IF v_prof IS NOT NULL THEN
            UPDATE proformas SET venta_id = v_venta WHERE id = v_prof;
        END IF;

        LOOP
            EXIT WHEN parte(v_lineas, k, ';') IS NULL;
            v_id := producto(parte(parte(v_lineas, k, ';'), 1, '|'));
            v_cant := TO_NUMBER(parte(parte(v_lineas, k, ';'), 2, '|'));
            v_desc := TO_NUMBER(parte(parte(v_lineas, k, ';'), 3, '|'));
            v_precio := precio(v_id);
            SELECT nombre, costo_base INTO v_nombre, v_costo FROM productos WHERE id = v_id;
            INSERT INTO detalle_venta (cantidad, precio_unitario, producto_id, venta_id, descuento_porcentaje,
                                       costo_unitario, descripcion, precio_catalogo)
            VALUES (v_cant, v_precio, v_id, v_venta, v_desc, v_costo, v_nombre, v_precio);
            IF v_prof IS NOT NULL THEN
                INSERT INTO items_proforma (cantidad, precio_unitario, proforma_id, producto_id, descuento_porcentaje,
                                            precio_catalogo)
                VALUES (v_cant, v_precio, v_prof, v_id, v_desc, v_precio);
            END IF;
            -- Como DetalleVenta.calcularSubtotalDetalle()
            v_sub := v_sub + ROUND(v_precio * v_cant * (1 - v_desc / 100), 2);
            k := k + 1;
        END LOOP;
        v_sub := ROUND(v_sub, 2);
        v_total := v_sub + ROUND(v_sub * c_igv, 2);

        -- Factura F001 aceptada (simulado), con su envío en el historial
        v_n_fact := v_n_fact + 1;
        v_numero := LPAD(v_n_fact, 8, '0');
        INSERT INTO comprobantes (enlace_pdf, enlace_xml, estado_sunat, fecha_emision, numero, serie, tipo, total, venta_id,
                                  sunat_codigo, sunat_mensaje, sunat_intentos, sunat_ultimo_intento, receptor_tipo_doc,
                                  receptor_num_doc, receptor_nombre, receptor_direccion, reemplazado, ambiente_emision)
        VALUES (c_url || 'F001-' || v_numero || '.pdf', c_url || 'F001-' || v_numero || '.xml', 'ACEPTADO', v_fecha,
                v_numero, 'F001', 'FACTURA', v_total, v_venta, 0, c_simulado, 1,
                CAST(v_fecha AS TIMESTAMP) + NUMTODSINTERVAL(10 + p_i, 'HOUR'), '6', v_cliente.ruc,
                v_cliente.nombreorazon_social, v_cliente.direccion, FALSE, 'SIMULADO')
        RETURNING id INTO v_comp;
        INSERT INTO envios_sunat (comprobante_id, intento, fecha, resultado, codigo, mensaje, ambiente)
        VALUES (v_comp, 1, CAST(v_fecha AS TIMESTAMP) + NUMTODSINTERVAL(10 + p_i, 'HOUR'), 'ACEPTADO', 0, c_simulado, 'SIMULADO');
        UPDATE ventas SET comprobante_id = v_comp WHERE id = v_venta;

        IF v_anulada THEN
            -- Anulada a los 3 días, con motivo y quién (como exige TRG_VENTA_ESTADO_BU), y su nota de crédito
            UPDATE ventas SET estado = 'ANULADA', fecha_anulacion = v_fecha + 3, anulado_por = 'lisandro',
                              motivo_anulacion = 'El cliente desistió de la compra antes de la instalación'
             WHERE id = v_venta;
            v_n_nota := v_n_nota + 1;
            INSERT INTO notas_credito (enlace_pdf, enlace_xml, estado_sunat, fecha_emision, motivo, numero, serie, total,
                                       comprobante_id, sunat_codigo, sunat_mensaje, sunat_intentos, sunat_ultimo_intento)
            VALUES (c_url || 'FC01-' || LPAD(v_n_nota, 8, '0') || '.pdf', c_url || 'FC01-' || LPAD(v_n_nota, 8, '0') || '.xml',
                    'ACEPTADO', v_fecha + 3, 'Anulación de la operación (venta #' || v_venta || ')', LPAD(v_n_nota, 8, '0'),
                    'FC01', v_total, v_comp, 0, c_simulado, 1, CAST(v_fecha + 3 AS TIMESTAMP) + NUMTODSINTERVAL(11, 'HOUR'))
            RETURNING id INTO v_id;
            INSERT INTO envios_sunat (nota_credito_id, intento, fecha, resultado, codigo, mensaje, ambiente)
            VALUES (v_id, 1, CAST(v_fecha + 3 AS TIMESTAMP) + NUMTODSINTERVAL(11, 'HOUR'), 'ACEPTADO', 0, c_simulado, 'SIMULADO');
        ELSIF v_credito THEN
            -- Cobranza: 2026 n.° 18 queda atrasada sin cobrar; 2026 n.° 15 con un abono parcial; el resto pagado
            DECLARE
                v_metodo VARCHAR2(20) := CASE MOD(p_i, 5) WHEN 0 THEN 'TRANSFERENCIA' WHEN 1 THEN 'DEPOSITO'
                                         WHEN 2 THEN 'YAPE_PLIN' WHEN 3 THEN 'TRANSFERENCIA' ELSE 'CHEQUE' END;
                v_parcial BOOLEAN := p_anio = 2026 AND p_i = 15;
                v_primero NUMBER := CASE WHEN MOD(p_i, 2) = 0 OR v_parcial THEN ROUND(v_total / 2, 2) ELSE v_total END;
                PROCEDURE cobrar(p_fecha DATE, p_monto NUMBER, p_n PLS_INTEGER) IS
                    v_cobro NUMBER;
                BEGIN
                    INSERT INTO cobros (fecha_cobro, metodo_pago, moneda, monto_total, referencia, cliente_id, estado,
                                        registrado_por)
                    VALUES (p_fecha, v_metodo, v_moneda, p_monto,
                            'OP-' || TO_CHAR(p_fecha, 'YYYYMMDD') || '-' || LPAD(v_venta, 3, '0') || p_n,
                            v_cliente.id, 'VIGENTE', 'lucy')
                    RETURNING id INTO v_cobro;
                    INSERT INTO detalle_cobro (monto, cobro_id, venta_id) VALUES (p_monto, v_cobro, v_venta);
                    v_n_cobros := v_n_cobros + 1;
                END;
            BEGIN
                IF NOT (p_anio = 2026 AND p_i = 18) THEN
                    cobrar(v_fecha + CASE WHEN v_primero < v_total THEN 10 ELSE 20 END, v_primero, 1);
                    IF v_primero < v_total AND NOT v_parcial THEN
                        cobrar(v_fecha + 25, v_total - v_primero, 2);
                    END IF;
                    IF NOT v_parcial THEN
                        UPDATE ventas SET estado_pago = 'PAGADO' WHERE id = v_venta;
                    END IF;
                END IF;
            END;
        END IF;

        -- Rutina de mantenimiento: 2 visitas, a los 6 y a los 12 meses
        IF p(p_i).rutina AND NOT v_anulada THEN
            FOR visita IN 1 .. 2 LOOP
                DECLARE
                    v_dia DATE := ADD_MONTHS(v_fecha, 6 * visita);
                    v_hecha BOOLEAN := v_dia < v_hoy - 20;
                BEGIN
                    INSERT INTO mantenimientos (estado, fecha_programada, notas, venta_id, fecha_realizada, alerta_enviada,
                                                cliente_id, tecnico)
                    VALUES (CASE WHEN v_hecha THEN 'REALIZADO' ELSE 'PENDIENTE' END, v_dia,
                            CASE WHEN v_hecha THEN 'Mantenimiento preventivo: limpieza de filtros y serpentines, '
                                 || 'revisión de presiones y conexiones eléctricas' END,
                            v_venta, CASE WHEN v_hecha THEN v_dia END, v_dia <= v_hoy + 7, v_cliente.id,
                            CASE WHEN v_hecha THEN 'Técnico de JD' END);
                    v_n_visit := v_n_visit + 1;
                END;
            END LOOP;
        END IF;
        v_n_ventas := v_n_ventas + 1;
    END;

    PROCEDURE plantilla(n PLS_INTEGER, l VARCHAR2, r BOOLEAN) IS
    BEGIN
        p(n).lineas := l;
        p(n).rutina := r;
    END;
BEGIN
    -- 20 plantillas de venta: "prefijo del producto|cantidad|descuento %"
    plantilla(1,  'Aire acondicionado split inverter 9 000|1|0;Tubería de cobre flexible 1/4|1|0;Tubería de cobre flexible 3/8|1|0;Soporte metálico|1|0', TRUE);
    plantilla(2,  'Aire acondicionado split inverter 12 000|2|5;Aislamiento térmico|6|0;Cinta vinílica|4|0', TRUE);
    plantilla(3,  'Gas refrigerante R410A|2|0;Capacitor de marcha|3|0', FALSE);
    plantilla(4,  'Aire acondicionado cassette|1|0', TRUE);
    plantilla(5,  'Extractor helicoidal|3|5', FALSE);
    plantilla(6,  'Aire acondicionado split inverter 18 000|1|5;Soporte metálico|1|0;Tubería de cobre flexible 1/2|1|0', TRUE);
    plantilla(7,  'Unidad condensadora|1|0;Evaporador para cámara|1|0', TRUE);
    plantilla(8,  'Gas refrigerante R404A|1|0;Nitrógeno seco|1|0', FALSE);
    plantilla(9,  'Vitrina refrigerada|1|5', TRUE);
    plantilla(10, 'Compresor rotativo|1|0;Capacitor de marcha|1|0;Gas refrigerante R32|1|0', FALSE);
    plantilla(11, 'Aire acondicionado piso-techo 48 000|1|0', TRUE);
    plantilla(12, 'Extractor centrífugo|2|10;Ventilador axial|1|0', FALSE);
    plantilla(13, 'Aire acondicionado split Dual Inverter 24 000|1|5;Tubería de cobre flexible 1/2|1|0;Soporte metálico|1|0', TRUE);
    plantilla(14, 'Tarjeta electrónica universal|2|0;Motor ventilador|1|0', FALSE);
    plantilla(15, 'Cámara frigorífica modular|1|0', TRUE);
    plantilla(16, 'Bomba de vacío|1|0;Manómetro digital|1|0', FALSE);
    plantilla(17, 'Aire acondicionado split inverter 12 000|1|0;Tubería de cobre flexible 1/4|1|0', FALSE);
    plantilla(18, 'Gas refrigerante R32|2|0;Aislamiento térmico|10|0;Cinta vinílica|5|0', FALSE);
    plantilla(19, 'Aire acondicionado piso-techo 60 000|1|0', TRUE);
    plantilla(20, 'Aire acondicionado split inverter 9 000|3|5;Tubería de cobre flexible 1/4|3|0;Soporte metálico|3|0', TRUE);

    -- Lo que se compra cada enero de cada material: lo que venden las 20 plantillas en un año + 5 de reserva
    FOR m IN (SELECT id FROM productos WHERE sector = 'MATERIAL_SERVICIO') LOOP
        v_compra_anual(m.id) := 5;
    END LOOP;
    FOR n IN 1 .. 20 LOOP
        FOR k IN 1 .. 5 LOOP
            EXIT WHEN parte(p(n).lineas, k, ';') IS NULL;
            DECLARE
                v_id NUMBER := producto(parte(parte(p(n).lineas, k, ';'), 1, '|'));
            BEGIN
                IF v_compra_anual.EXISTS(v_id) THEN
                    v_compra_anual(v_id) := v_compra_anual(v_id) + TO_NUMBER(parte(parte(p(n).lineas, k, ';'), 2, '|'));
                END IF;
            END;
        END LOOP;
    END LOOP;

    -- Los materiales arrancan en 0: su stock lo dan las compras
    UPDATE productos SET stock_actual = 0, ultimo_costo_compra = NULL, fecha_ultima_compra = NULL
     WHERE sector = 'MATERIAL_SERVICIO';

    FOR anio IN 2024 .. 2026 LOOP
        compra(TO_DATE(anio || '-01-03', 'YYYY-MM-DD'), 'DE RIGO', 'F001-' || LPAD((anio - 2023) * 100 + 1, 8, '0'),
               'Gas refrigerante R410A;Gas refrigerante R32;Gas refrigerante R404A', FALSE);
        compra(TO_DATE(anio || '-01-04', 'YYYY-MM-DD'), 'LINDE', 'F004-' || LPAD((anio - 2023) * 100 + 2, 8, '0'),
               'Nitrógeno seco', FALSE);
        compra(TO_DATE(anio || '-01-05', 'YYYY-MM-DD'), 'HOMECENTERS', 'F112-' || LPAD((anio - 2023) * 100 + 3, 8, '0'),
               'Tubería de cobre flexible 1/4;Tubería de cobre flexible 3/8;Tubería de cobre flexible 1/2;'
               || 'Aislamiento térmico;Soporte metálico;Cinta vinílica', FALSE);
        compra(TO_DATE(anio || '-01-06', 'YYYY-MM-DD'), 'REFRIGERACIONES M', 'E001-' || LPAD((anio - 2023) * 100 + 4, 8, '0'),
               'Bomba de vacío;Manómetro digital;Capacitor de marcha;Compresor rotativo;Tarjeta electrónica universal;'
               || 'Motor ventilador', FALSE);
        FOR i IN 1 .. 20 LOOP
            venta(anio, i);
        END LOOP;
    END LOOP;
    -- Reposición de octubre de 2026, a crédito y todavía sin pagar (Cuentas por pagar)
    compra(DATE '2026-10-20', 'HOMECENTERS', 'F112-00000401',
           'Tubería de cobre flexible 1/4|10;Aislamiento térmico|40;Cinta vinílica|20', TRUE);

    -- Una proforma PENDIENTE de noviembre de 2026 (todavía sin respuesta del cliente)
    DECLARE
        v_prof NUMBER;
        v_id NUMBER := producto('Aire acondicionado split inverter 18 000');
        v_precio NUMBER := precio(v_id);
    BEGIN
        INSERT INTO proformas (estado, fecha_emision, fecha_vencimiento, moneda, cliente_id, tipo_cambio,
                               enviado_correo, enviado_whatsapp)
        VALUES ('PENDIENTE', DATE '2026-11-04', DATE '2026-11-19', 'SOLES', 6, 3.47, TRUE, FALSE)
        RETURNING id INTO v_prof;
        INSERT INTO items_proforma (cantidad, precio_unitario, proforma_id, producto_id, descuento_porcentaje, precio_catalogo)
        VALUES (2, v_precio, v_prof, v_id, 5, v_precio);
    END;

    -- Stock = compras − ventas no anuladas (como lo cuenta el kárdex)
    UPDATE productos pr
       SET stock_actual = NVL((SELECT SUM(d.cantidad) FROM detalle_compra d WHERE d.producto_id = pr.id), 0)
                        - NVL((SELECT SUM(d.cantidad) FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
                                WHERE d.producto_id = pr.id AND v.estado = 'REGISTRADA'), 0)
     WHERE sector = 'MATERIAL_SERVICIO';

    -- Correlativos: la próxima factura será la F001-00000061 y la próxima nota la FC01-00000004
    MERGE INTO series_comprobante s
    USING (SELECT 'F001' serie, v_n_fact n FROM dual UNION ALL SELECT 'B001', 0 FROM dual
           UNION ALL SELECT 'FC01', v_n_nota FROM dual UNION ALL SELECT 'BC01', 0 FROM dual) x
       ON (s.serie = x.serie)
     WHEN MATCHED THEN UPDATE SET s.ultimo_numero = x.n
     WHEN NOT MATCHED THEN INSERT (serie, ultimo_numero) VALUES (x.serie, x.n);

    -- La carga no es actividad de usuarios: la auditoría de ventas y el registro de errores (BD2) arrancan vacíos
    BEGIN
        EXECUTE IMMEDIATE 'DELETE FROM venta_auditoria';
        EXECUTE IMMEDIATE 'DELETE FROM log_errores';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE <> -942 THEN RAISE; END IF;
    END;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Ventas: ' || v_n_ventas || ' · facturas: ' || v_n_fact || ' · notas de crédito: ' || v_n_nota
                         || ' · cobros: ' || v_n_cobros || ' · visitas de mantenimiento: ' || v_n_visit);
END;
/

-- Resumen por año
SET FEEDBACK ON LINESIZE 200 PAGESIZE 100
COLUMN anio FORMAT A6
SELECT TO_CHAR(fecha_venta, 'YYYY') AS anio, COUNT(*) AS ventas,
       SUM(CASE WHEN estado = 'ANULADA' THEN 1 ELSE 0 END) AS anuladas,
       SUM(CASE WHEN tipo_pago = 'CREDITO' THEN 1 ELSE 0 END) AS a_credito,
       SUM(CASE WHEN estado = 'REGISTRADA' AND estado_pago = 'PENDIENTE' THEN 1 ELSE 0 END) AS por_cobrar,
       SUM(CASE WHEN proforma_id IS NOT NULL THEN 1 ELSE 0 END) AS desde_proforma,
       MIN(fecha_venta) AS primera, MAX(fecha_venta) AS ultima
FROM ventas GROUP BY TO_CHAR(fecha_venta, 'YYYY') ORDER BY 1;
EXIT
