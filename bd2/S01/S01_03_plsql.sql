-- ============================================================
-- BD2 · S1 · Paso 3: motor transaccional PL/SQL de la venta de SGE-JD.
-- Se ejecuta como el propietario JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S01\S01_03_plsql.sql
--
-- Operación transaccional: registrar una venta directa al contado (cabecera VENTAS + línea DETALLE_VENTA)
-- descontando el stock del material vendido. Las fórmulas son las mismas del backend:
--   línea    = ROUND(precio × cantidad × (1 − descuento/100), 2)       DetalleVenta.calcularSubtotalDetalle()
--   subtotal = suma de líneas;  IGV = ROUND(subtotal × 0.18, 2);  total = subtotal + IGV   Venta.calcularTotal()
--   precio de catálogo = costo_base × (1 + margen/100)                  Producto.calcularPrecioVenta()
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue

-- Función 1 (parámetros IN): importe de una línea, sin IGV, con su descuento
CREATE OR REPLACE FUNCTION fn_subtotal_linea(
    p_cantidad  IN NUMBER,
    p_precio    IN NUMBER,
    p_descuento IN NUMBER DEFAULT 0
) RETURN NUMBER IS
BEGIN
    IF p_cantidad IS NULL OR p_cantidad <= 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'La cantidad debe ser mayor que cero.');
    END IF;
    IF p_precio IS NULL OR p_precio < 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'El precio no puede ser negativo.');
    END IF;
    IF NVL(p_descuento, 0) NOT BETWEEN 0 AND 100 THEN
        RAISE_APPLICATION_ERROR(-20003, 'El descuento debe estar entre 0 y 100 %.');
    END IF;
    RETURN ROUND(p_precio * p_cantidad * (1 - NVL(p_descuento, 0) / 100), 2);
END fn_subtotal_linea;
/

-- Función 2 (parámetro IN): total con IGV de una venta ya registrada, calculado desde sus líneas
CREATE OR REPLACE FUNCTION fn_total_venta(p_venta_id IN NUMBER) RETURN NUMBER IS
    v_subtotal NUMBER;
    v_lineas   NUMBER;
BEGIN
    SELECT COUNT(*), ROUND(NVL(SUM(fn_subtotal_linea(cantidad, precio_unitario, descuento_porcentaje)), 0), 2)
    INTO v_lineas, v_subtotal
    FROM detalle_venta
    WHERE venta_id = p_venta_id;

    IF v_lineas = 0 THEN
        RAISE_APPLICATION_ERROR(-20004, 'La venta ' || p_venta_id || ' no existe o no tiene líneas.');
    END IF;
    RETURN v_subtotal + ROUND(v_subtotal * 0.18, 2);
END fn_total_venta;
/

-- Procedimiento (parámetros IN y OUT): registra una venta al contado de un producto
CREATE OR REPLACE PROCEDURE sp_registrar_venta(
    p_cliente_id  IN  NUMBER,
    p_producto_id IN  NUMBER,
    p_cantidad    IN  NUMBER,
    p_descuento   IN  NUMBER,
    p_rol         IN  VARCHAR2,      -- 'ADMIN' o 'ASISTENTE': decide el tope de descuento
    p_vendedor    IN  VARCHAR2,
    p_venta_id    OUT NUMBER,
    p_total       OUT NUMBER
) IS
    v_existe        NUMBER;
    v_sector        productos.sector%TYPE;
    v_moneda        productos.moneda%TYPE;
    v_stock         productos.stock_actual%TYPE;
    v_precio        NUMBER(12,2);
    v_costo         NUMBER(12,2);
    v_tope          NUMBER(5,2);
    v_subtotal      NUMBER(12,2);
BEGIN
    -- Regla 1: el cliente existe
    SELECT COUNT(*) INTO v_existe FROM clientes WHERE id = p_cliente_id;
    IF v_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20010, 'El cliente ' || p_cliente_id || ' no existe.');
    END IF;

    -- Regla 2: el producto existe; se bloquea su fila para que otra venta simultánea no use el mismo stock
    SELECT sector, moneda, NVL(stock_actual, 0),
           ROUND(costo_base * (1 + margen_porcentaje / 100), 2),
           NVL(ultimo_costo_compra, costo_base)
    INTO v_sector, v_moneda, v_stock, v_precio, v_costo
    FROM productos
    WHERE id = p_producto_id
    FOR UPDATE;

    IF v_moneda <> 'SOLES' THEN
        RAISE_APPLICATION_ERROR(-20011, 'S1 solo registra productos en soles (los de dólares necesitan tipo de cambio).');
    END IF;

    -- Regla 3: el descuento no supera el tope del rol (TOPES_DESCUENTO; 30 % y 10 % si nadie los cambió)
    BEGIN
        SELECT CASE WHEN UPPER(p_rol) = 'ADMIN' THEN tope_admin ELSE tope_asistente END
        INTO v_tope FROM topes_descuento WHERE id = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_tope := CASE WHEN UPPER(p_rol) = 'ADMIN' THEN 30 ELSE 10 END;
    END;
    IF NVL(p_descuento, 0) > v_tope THEN
        RAISE_APPLICATION_ERROR(-20012, 'El descuento de ' || p_descuento || ' % supera el tope de ' || v_tope || ' %.');
    END IF;

    -- Regla 4: solo el material de servicio controla stock (Producto.controlaStock())
    IF v_sector = 'MATERIAL_SERVICIO' AND v_stock < p_cantidad THEN
        RAISE_APPLICATION_ERROR(-20013, 'Stock insuficiente: hay ' || v_stock || ' y se piden ' || p_cantidad || '.');
    END IF;

    -- Regla 5: hasta 700 soles con IGV (sobre ese monto corresponde detracción o retención, que S1 no calcula)
    v_subtotal := fn_subtotal_linea(p_cantidad, v_precio, p_descuento);
    p_total := v_subtotal + ROUND(v_subtotal * 0.18, 2);
    IF p_total > 700 THEN
        RAISE_APPLICATION_ERROR(-20014, 'Total de ' || p_total || ' soles: supera 700 y necesita detracción o retención (usar el sistema).');
    END IF;

    INSERT INTO ventas (cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion,
                        vendedor_nombre)
    VALUES (p_cliente_id, TRUNC(SYSDATE), 'SOLES', 'CONTADO', 'PAGADO', 'REGISTRADA', 'NINGUNA', p_vendedor)
    RETURNING id INTO p_venta_id;

    INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, descuento_porcentaje,
                               costo_unitario, precio_catalogo)
    VALUES (p_venta_id, p_producto_id, p_cantidad, v_precio, NVL(p_descuento, 0), v_costo, v_precio);

    IF v_sector = 'MATERIAL_SERVICIO' THEN
        UPDATE productos SET stock_actual = stock_actual - p_cantidad WHERE id = p_producto_id;
    END IF;
    -- Sin COMMIT: confirma quien llama (el backend o el bloque de prueba), igual que una transacción JPA.
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20015, 'El producto ' || p_producto_id || ' no existe.');
END sp_registrar_venta;
/

SHOW ERRORS

SELECT object_name, object_type, status
FROM user_objects
WHERE object_name IN ('FN_SUBTOTAL_LINEA', 'FN_TOTAL_VENTA', 'SP_REGISTRAR_VENTA')
ORDER BY object_name;

-- El usuario técnico solo puede EJECUTAR la operación; no toca las tablas directamente
GRANT EXECUTE ON sp_registrar_venta TO jd_app;
GRANT EXECUTE ON fn_total_venta     TO jd_app;
exit
