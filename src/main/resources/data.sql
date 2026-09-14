INSERT INTO
    PRODUCTOS (
        nombre,
        descripcion,
        sector,
        costo_base,
        margen_porcentaje,
        moneda,
        stock_actual,
        stock_minimo,
        visible_en_catalogo
    )
VALUES (
        'Tubería de Cobre 3/8',
        'Tubería para aire acondicionado',
        'MATERIAL_SERVICIO',
        15.00,
        20.00,
        'SOLES',
        5,
        10,
        1
    );

INSERT INTO
    PROVEEDORES (
        tipo,
        nombre_o_razon_social,
        ruc,
        direccion,
        telefono,
        correo,
        contacto_vendedor
    )
VALUES (
        'EMPRESA',
        'RefriPartes S.A.C.',
        '20123456789',
        'Av. Industrial 123',
        '987654321',
        'ventas@refripartes.com',
        'Juan Perez'
    );

-- Datos de prueba para facturación
INSERT INTO
    clientes (
        nombreorazon_social,
        tipo,
        ruc,
        dni,
        direccion,
        correo,
        telefono
    )
VALUES (
        'SUPERMERCADOS PERUANOS S.A.C.',
        'EMPRESA',
        '20100070970',
        NULL,
        'Calle Morelli 181 Int. P-2, San Borja',
        'yoelwagnercs@gmail.com',
        '910099127'
    );

-- (Nota: El formato de fechas e IDs depende de Hibernate. cliente_id = 1, producto_id = 1)
INSERT INTO
    VENTAS (
        cliente_id,
        fecha_venta,
        tipo_pago,
        estado_pago,
        moneda
    )
VALUES (
        1,
        CURRENT_DATE,
        'CONTADO',
        'PAGADO',
        'SOLES'
    );

INSERT INTO
    detalle_venta (
        venta_id,
        producto_id,
        cantidad,
        precio_unitario
    )
VALUES (1, 1, 2, 20.00);

-- Usuarios de prueba (contraseña para ambos: 123456)
INSERT INTO
    USUARIOS (username, password, rol)
VALUES (
        'lisandro',
        '{bcrypt}$2a$10$x3bKLenLoyPJr/0061bZZugyP.pKw0jk2GJj5gduVQx6S0IyTWBTm',
        'ADMIN'
    );

INSERT INTO
    USUARIOS (username, password, rol)
VALUES (
        'lucy',
        '{bcrypt}$2a$10$x3bKLenLoyPJr/0061bZZugyP.pKw0jk2GJj5gduVQx6S0IyTWBTm',
        'ASISTENTE'
    );

-- 50 Productos Generados Automáticamente
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Aire Acondicionado Split 12000 BTU', 'Producto para REFRIGERACION', 'REFRIGERACION', 2160.02, 38.5, 'SOLES', 8, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Compresor Hermético 1/3 HP', 'Producto para REFRIGERACION', 'REFRIGERACION', 2158.26, 19.34, 'SOLES', 9, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Termostato Digital', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 71.52, 17.37, 'DOLARES', 18, 3, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Filtro Secador de Línea', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 98.64, 43.92, 'DOLARES', 19, 5, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Válvula de Expansión Termostática', 'Producto para REFRIGERACION', 'REFRIGERACION', 619.54, 34.71, 'SOLES', 17, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Gas Refrigerante R410a (Cilindro)', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 226.26, 21.04, 'DOLARES', 176, 11, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Extractor Centrífugo en Línea', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 195.72, 38.82, 'SOLES', 27, 11, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Cortina de Aire 120cm', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 86.24, 26.12, 'SOLES', 12, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Rejilla de Retorno de Aluminio', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 1821.57, 42.93, 'DOLARES', 4, 5, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Bomba de Condensado', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 233.3, 27.01, 'SOLES', 10, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Tubo de Aislamiento Armaflex', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 152.98, 41.92, 'DOLARES', 64, 11, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Termómetro de Bolsillo', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 70.28, 28.94, 'SOLES', 12, 3, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Manómetro para Múltiples Gases', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 1234.5, 20.72, 'SOLES', 19, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Evaporador de Techo', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 891.61, 16.49, 'SOLES', 8, 5, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Condensador Enfriado por Aire', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 49.34, 37.87, 'DOLARES', 105, 7, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Gas Refrigerante R134a', 'Producto para REFRIGERACION', 'REFRIGERACION', 1022.5, 31.04, 'DOLARES', 19, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Controlador de Temperatura', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 2809.32, 28.86, 'SOLES', 6, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Interruptor de Presión', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 276.68, 43.85, 'SOLES', 178, 10, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Filtro de Aire Plisado', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 1371.05, 26.95, 'SOLES', 15, 3, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Campana Extractora Industrial', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 1163.68, 35.82, 'DOLARES', 9, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Ventilador Axial', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 124.61, 24.27, 'SOLES', 12, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Tubería de Cobre 1/2', 'Producto para REFRIGERACION', 'REFRIGERACION', 265.44, 27.89, 'DOLARES', 18, 5, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Cinta de Aluminio Adhesiva', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 252.14, 22.68, 'DOLARES', 106, 9, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Motor Ventilador para Evaporador', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 285.64, 29.39, 'SOLES', 65, 12, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Bomba de Vacío 2 Etapas', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 18.97, 42.4, 'SOLES', 8, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Manguera para Manómetro', 'Producto para REFRIGERACION', 'REFRIGERACION', 2461.17, 40.59, 'SOLES', 4, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Termostato Bimetálico', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 176.88, 39.23, 'DOLARES', 182, 8, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Detector de Fugas de Gas', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 63.2, 36.55, 'SOLES', 8, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Soldadura de Plata al 15%', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 260.4, 36.77, 'SOLES', 165, 11, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Gas Refrigerante R22 (Reemplazo)', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 293.45, 27.6, 'DOLARES', 112, 13, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Aire Acondicionado Cassette 24000 BTU', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 143.17, 44.2, 'SOLES', 7, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Tablero de Control Eléctrico', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 33.56, 35.16, 'DOLARES', 22, 7, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Protector de Voltaje', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 906.96, 21.55, 'SOLES', 2, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Capacitor de Trabajo 45 uF', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 113.67, 32.66, 'DOLARES', 16, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Contactora de 2 Polos', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 231.5, 30.88, 'SOLES', 16, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Sensor NTC 10k', 'Producto para CLIMATIZACION', 'CLIMATIZACION', 647.4, 21.61, 'SOLES', 10, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Tubería de Cobre 1/4', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 267.05, 40.27, 'SOLES', 85, 15, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Gas Refrigerante R404a', 'Producto para REFRIGERACION', 'REFRIGERACION', 669.74, 40.69, 'SOLES', 12, 3, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Difusor de Aire Cuadrado', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 154.88, 15.13, 'SOLES', 87, 8, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Módulo Inverter Universal', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 227.36, 35.11, 'SOLES', 20, 3, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Compresor Scroll 3 HP', 'Producto para REFRIGERACION', 'REFRIGERACION', 2491.78, 15.65, 'SOLES', 11, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Recibidor de Líquido', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 167.12, 27.97, 'DOLARES', 7, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Separador de Aceite', 'Producto para REFRIGERACION', 'REFRIGERACION', 1134.3, 43.53, 'DOLARES', 17, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Válvula Solenoide', 'Producto para VENTILACION_FORZADA', 'VENTILACION_FORZADA', 73.52, 15.13, 'SOLES', 14, 1, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Mirilla de Líquido', 'Producto para REFRIGERACION', 'REFRIGERACION', 2749.65, 38.18, 'DOLARES', 7, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Evaporador Cúbico Comercial', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 72.89, 15.09, 'DOLARES', 56, 15, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Cámara Frigorífica (Panel)', 'Producto para MATERIAL_SERVICIO', 'MATERIAL_SERVICIO', 295.71, 24.73, 'SOLES', 127, 15, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Termostato de Ambiente', 'Producto para REFRIGERACION', 'REFRIGERACION', 1105.3, 37.88, 'SOLES', 12, 4, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Aire Acondicionado Portátil 10000 BTU', 'Producto para REFRIGERACION', 'REFRIGERACION', 2585.05, 25.64, 'DOLARES', 7, 2, 1);
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo) 
VALUES ('Motor Extractor de 1 HP', 'Producto para REFRIGERACION', 'REFRIGERACION', 1164.67, 27.71, 'SOLES', 7, 3, 1);
