-- ============================================================
-- BD2 · S3 · Paso 3: SP_REGISTRAR_VENTA y FN_TOTAL_VENTA con manejo de excepciones (versión 2).
-- Como JDREFRIGERACION:  .\bd2\herramientas\sql.ps1 bd2\S03\S03_03_plsql.sql
--
-- Errores reales que S1 no manejaba:
--   1. FOR UPDATE sin límite: si otra sesión (el backend) tenía el producto bloqueado, el procedimiento
--      esperaba para siempre. Ahora espera 10 s (igual que VentaRepository.bloquear) y responde ORA-20017
--      (Oracle da ORA-00054 al vencer la espera; ver hallazgo 2 en README).
--   2. Vendedor de más de 60 caracteres: salía ORA-12899 crudo, con el nombre interno de la columna.
--   3. Un solo WHEN NO_DATA_FOUND al final del procedimiento: cualquier NO_DATA_FOUND se informaba como
--      "el producto no existe". Ahora cada SELECT INTO tiene su propio bloque.
--   4. Ningún error quedaba registrado.
-- Regla: cada error se registra (SP_REGISTRAR_ERROR, transacción autónoma) ANTES de relanzarse; nunca
-- WHEN OTHERS THEN NULL.
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue

CREATE OR REPLACE PROCEDURE sp_registrar_venta(
    p_cliente_id  IN  NUMBER,
    p_producto_id IN  NUMBER,
    p_cantidad    IN  NUMBER,
    p_descuento   IN  NUMBER,
    p_rol         IN  VARCHAR2,
    p_vendedor    IN  VARCHAR2,
    p_venta_id    OUT NUMBER,
    p_total       OUT NUMBER
) IS
    c_objeto   CONSTANT VARCHAR2(30) := 'SP_REGISTRAR_VENTA';
    -- Excepciones de Oracle con nombre propio (PRAGMA EXCEPTION_INIT)
    -- Fila bloqueada por otra transacción. En Oracle 23 el FOR UPDATE WAIT vencido da ORA-00054
    -- (comprobado en S03_05); versiones anteriores daban ORA-30006. Se nombran los dos.
    e_fila_ocupada     EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_fila_ocupada, -54);
    e_fila_ocupada_19c EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_fila_ocupada_19c, -30006);
    e_valor_largo    EXCEPTION;   -- ORA-12899: value too large for column
    PRAGMA EXCEPTION_INIT(e_valor_largo, -12899);

    v_contexto VARCHAR2(500) := 'cliente=' || p_cliente_id || ' producto=' || p_producto_id
                                || ' cantidad=' || p_cantidad || ' descuento=' || p_descuento || ' rol=' || p_rol;
    v_cliente  clientes.id%TYPE;
    v_sector   productos.sector%TYPE;
    v_moneda   productos.moneda%TYPE;
    v_stock    productos.stock_actual%TYPE;
    v_precio   NUMBER(12,2);
    v_costo    NUMBER(12,2);
    v_tope     NUMBER(5,2);
    v_subtotal NUMBER(12,2);

    -- Registra y lanza un error de negocio (códigos -20010 a -20019)
    PROCEDURE fallar(p_codigo NUMBER, p_mensaje VARCHAR2) IS
    BEGIN
        sp_registrar_error(c_objeto, p_codigo, p_mensaje, v_contexto);
        RAISE_APPLICATION_ERROR(p_codigo, p_mensaje);
    END fallar;
BEGIN
    -- Cliente: excepción PREDEFINIDA NO_DATA_FOUND (SELECT INTO sin filas)
    BEGIN
        SELECT id INTO v_cliente FROM clientes WHERE id = p_cliente_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            sp_registrar_error(c_objeto, SQLCODE, 'Cliente no encontrado: ' || p_cliente_id, v_contexto);
            RAISE_APPLICATION_ERROR(-20010, 'El cliente ' || p_cliente_id || ' no existe.');
    END;

    -- Producto: NO_DATA_FOUND y fila ocupada por otra transacción (espera máxima 10 s)
    BEGIN
        SELECT sector, moneda, NVL(stock_actual, 0),
               ROUND(costo_base * (1 + margen_porcentaje / 100), 2),
               NVL(ultimo_costo_compra, costo_base)
        INTO v_sector, v_moneda, v_stock, v_precio, v_costo
        FROM productos
        WHERE id = p_producto_id
        FOR UPDATE WAIT 10;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            sp_registrar_error(c_objeto, SQLCODE, 'Producto no encontrado: ' || p_producto_id, v_contexto);
            RAISE_APPLICATION_ERROR(-20015, 'El producto ' || p_producto_id || ' no existe.');
        WHEN e_fila_ocupada OR e_fila_ocupada_19c THEN
            sp_registrar_error(c_objeto, SQLCODE, SQLERRM, v_contexto);
            RAISE_APPLICATION_ERROR(-20017, 'El producto ' || p_producto_id
                || ' está siendo usado por otra venta o compra. Intente de nuevo en unos segundos.');
    END;

    -- Reglas de negocio: excepciones PERSONALIZADAS con RAISE_APPLICATION_ERROR
    IF p_cantidad IS NULL OR p_cantidad <= 0 THEN
        fallar(-20016, 'La cantidad debe ser mayor que cero.');
    END IF;
    IF v_moneda <> 'SOLES' THEN
        fallar(-20011, 'Solo se registran productos en soles (los de dólares necesitan tipo de cambio).');
    END IF;

    BEGIN
        SELECT CASE WHEN UPPER(p_rol) = 'ADMIN' THEN tope_admin ELSE tope_asistente END
        INTO v_tope FROM topes_descuento WHERE id = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN   -- esperado: nadie cambió los topes todavía; no es un error
            v_tope := CASE WHEN UPPER(p_rol) = 'ADMIN' THEN 30 ELSE 10 END;
    END;
    IF NVL(p_descuento, 0) > v_tope THEN
        fallar(-20012, 'El descuento de ' || p_descuento || ' % supera el tope de ' || v_tope || ' %.');
    END IF;
    IF v_sector = 'MATERIAL_SERVICIO' AND v_stock < p_cantidad THEN
        fallar(-20013, 'Stock insuficiente: hay ' || v_stock || ' y se piden ' || p_cantidad || '.');
    END IF;

    v_subtotal := fn_subtotal_linea(p_cantidad, v_precio, p_descuento);
    p_total := v_subtotal + ROUND(v_subtotal * 0.18, 2);
    IF p_total > 700 THEN
        fallar(-20014, 'Total de ' || p_total || ' soles: supera 700 y necesita detracción o retención (usar el sistema).');
    END IF;

    INSERT INTO ventas (cliente_id, fecha_venta, moneda, tipo_pago, estado_pago, estado, tipo_deduccion,
                        vendedor_nombre)
    VALUES (v_cliente, TRUNC(SYSDATE), 'SOLES', 'CONTADO', 'PAGADO', 'REGISTRADA', 'NINGUNA', p_vendedor)
    RETURNING id INTO p_venta_id;

    INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, descuento_porcentaje,
                               costo_unitario, precio_catalogo)
    VALUES (p_venta_id, p_producto_id, p_cantidad, v_precio, NVL(p_descuento, 0), v_costo, v_precio);

    IF v_sector = 'MATERIAL_SERVICIO' THEN
        UPDATE productos SET stock_actual = stock_actual - p_cantidad WHERE id = p_producto_id;
    END IF;
EXCEPTION
    WHEN e_valor_largo THEN
        sp_registrar_error(c_objeto, SQLCODE, SQLERRM, v_contexto);
        RAISE_APPLICATION_ERROR(-20018, 'Un dato es demasiado largo (el nombre del vendedor admite 60 caracteres).');
    WHEN OTHERS THEN
        -- Los -200xx ya se registraron donde se lanzaron; el resto (lo no previsto) se registra aquí.
        -- En los dos casos se RELANZA: nunca se traga un error.
        IF SQLCODE NOT BETWEEN -20999 AND -20000 THEN
            sp_registrar_error(c_objeto, SQLCODE, SQLERRM, v_contexto);
        END IF;
        RAISE;
END sp_registrar_venta;
/
SHOW ERRORS

-- FN_TOTAL_VENTA: ahora también deja registro cuando la venta no existe
CREATE OR REPLACE FUNCTION fn_total_venta(p_venta_id IN NUMBER) RETURN NUMBER IS
    v_subtotal NUMBER;
    v_lineas   NUMBER;
BEGIN
    SELECT COUNT(*), ROUND(NVL(SUM(fn_subtotal_linea(cantidad, precio_unitario, descuento_porcentaje)), 0), 2)
    INTO v_lineas, v_subtotal
    FROM detalle_venta
    WHERE venta_id = p_venta_id;

    IF v_lineas = 0 THEN
        sp_registrar_error('FN_TOTAL_VENTA', -20004, 'Venta sin líneas o inexistente', 'venta=' || p_venta_id);
        RAISE_APPLICATION_ERROR(-20004, 'La venta ' || p_venta_id || ' no existe o no tiene líneas.');
    END IF;
    RETURN v_subtotal + ROUND(v_subtotal * 0.18, 2);
END fn_total_venta;
/
SHOW ERRORS

SELECT object_name, object_type, status FROM user_objects
WHERE object_name IN ('SP_REGISTRAR_VENTA', 'FN_TOTAL_VENTA', 'SP_REGISTRAR_ERROR', 'FN_SUBTOTAL_LINEA')
ORDER BY object_name;
exit
