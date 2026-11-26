-- SOLO DESARROLLO: elimina los datos de negocio y carga cinco ejemplos por modulo.
-- Conservar usuarios Keycloak, configuracion de empresa y topes de descuento.
-- Respaldo obligatorio antes de COMMIT. Ejecutar primero con ROLLBACK (ensayo).
-- Uso: @datos_demo_cinco.sql ROLLBACK|COMMIT
-- Sin DDL ni reinicio de identidades: toda la limpieza y carga es una transaccion.
-- Cinco contratos trimestrales generan 20 visitas. Se agregan cinco ventas
-- anuladas para sus cinco notas de credito/devoluciones, ademas de cinco activas.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET SERVEROUTPUT ON FEEDBACK OFF VERIFY OFF DEFINE ON
ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION;

DECLARE
    fin VARCHAR2(10) := UPPER('&1');
    hoy CONSTANT DATE := DATE '2026-11-23';
    TYPE ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    clientes_ids ids; proveedores_ids ids; categorias_ids ids; productos_ids ids;
    proformas_ids ids; ventas_ids ids; sedes_ids ids;
    nombres_clientes SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        '[Demo] Frio Andino SAC', '[Demo] Mercado Los Pinos SAC',
        '[Demo] Clinica Horizonte SAC', '[Demo] Hotel Costa Azul SAC', '[Demo] Panaderia Buen Trigo SAC');
    nombres_proveedores SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        '[Demo] Suministros Termicos SAC', '[Demo] Cobre Industrial SAC',
        '[Demo] Repuestos Polar SAC', '[Demo] Aislamientos Lima SAC', '[Demo] Equipos Ventisca SAC');
    categorias SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        'Equipos de climatizacion', 'Mantenimiento preventivo', 'Instalacion de equipos',
        'Tuberias y accesorios', 'Aislamiento termico');
    productos_nombres SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        'Aire acondicionado inverter 12000 BTU', 'Mantenimiento preventivo de aire acondicionado',
        'Servicio de instalacion de aire acondicionado', 'Tuberia de cobre por metro', 'Aislamiento termico por metro');
    v_id NUMBER; v_compra NUMBER; v_pago NUMBER; v_contrato NUMBER;
    v_equipo NUMBER; v_mantenimiento NUMBER; v_orden NUMBER; v_venta NUMBER; v_comp NUMBER; v_nota NUMBER;
    v_fecha DATE; v_inicio DATE; v_sub NUMBER; v_total NUMBER; v_abono NUMBER;
    v_stock NUMBER; v_codigo VARCHAR2(20); v_nombre VARCHAR2(150); v_ruc VARCHAR2(11); v_direccion VARCHAR2(200);
    n NUMBER;

    -- Documentos deliberadamente ficticios; no se consulta ningun proveedor externo.
    FUNCTION ruc_demo(prefijo NUMBER) RETURN VARCHAR2 IS
        base VARCHAR2(10) := TO_CHAR(prefijo, 'FM0000000000');
        suma NUMBER := 0; digito NUMBER;
        pesos SYS.ODCINUMBERLIST := SYS.ODCINUMBERLIST(5,4,3,2,7,6,5,4,3,2);
    BEGIN
        FOR k IN 1..10 LOOP suma := suma + TO_NUMBER(SUBSTR(base,k,1)) * pesos(k); END LOOP;
        digito := 11 - MOD(suma,11);
        IF digito = 10 THEN digito := 0; ELSIF digito = 11 THEN digito := 1; END IF;
        RETURN base || TO_CHAR(digito);
    END;

    PROCEDURE comprobar(cantidad NUMBER, esperada NUMBER, etiqueta VARCHAR2) IS
    BEGIN
        IF cantidad <> esperada THEN
            RAISE_APPLICATION_ERROR(-20901, etiqueta || ': esperado ' || esperada || ', obtenido ' || cantidad);
        END IF;
        DBMS_OUTPUT.PUT_LINE(RPAD(etiqueta,32) || cantidad);
    END;

    PROCEDURE factura(venta NUMBER, cliente NUMBER, numero NUMBER, fecha DATE, total NUMBER, resultado OUT NUMBER) IS
        c clientes%ROWTYPE;
    BEGIN
        SELECT * INTO c FROM clientes WHERE id = cliente;
        INSERT INTO comprobantes (estado_sunat, fecha_emision, numero, serie, tipo, total, venta_id,
            sunat_codigo, sunat_mensaje, sunat_intentos, sunat_ultimo_intento, receptor_tipo_doc,
            receptor_num_doc, receptor_nombre, receptor_direccion, reemplazado, ambiente_emision)
        VALUES ('ACEPTADO', fecha, LPAD(numero,8,'0'), 'F001', 'FACTURA', total, venta,
            0, 'DEMO: simulacion local, sin envio ni validez tributaria', 1, CAST(fecha AS TIMESTAMP),
            '6', c.ruc, c.nombreorazon_social, c.direccion, FALSE, 'SIMULADO') RETURNING id INTO resultado;
        INSERT INTO envios_sunat (comprobante_id, intento, fecha, resultado, codigo, mensaje, ambiente)
        VALUES (resultado, 1, CAST(fecha AS TIMESTAMP), 'ACEPTADO', 0, 'DEMO: sin envio real', 'SIMULADO');
        UPDATE ventas SET comprobante_id = resultado WHERE id = venta;
    END;
BEGIN
    IF fin NOT IN ('ROLLBACK','COMMIT') OR SYS_CONTEXT('USERENV','CON_NAME') <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20902, 'Se requiere FREEPDB1 y argumento ROLLBACK o COMMIT');
    END IF;

    -- Referencias circulares: solo se sueltan los enlaces opcionales.
    UPDATE mantenimientos SET orden_trabajo_id = NULL;
    UPDATE equipos_instalados SET orden_trabajo_id = NULL, venta_id = NULL;
    UPDATE ordenes_trabajo SET equipo_id = NULL, mantenimiento_id = NULL, venta_id = NULL, venta_facturada_id = NULL;
    UPDATE proformas SET venta_id = NULL;
    UPDATE ventas SET proforma_id = NULL, comprobante_id = NULL;
    FOR t IN (SELECT COLUMN_VALUE tabla FROM TABLE(SYS.ODCIVARCHAR2LIST(
        'DEVOLUCIONES','ENVIOS_SUNAT','INTENTOS_CORREO','ENVIOS_CORREO','NOTAS_CREDITO','COMPROBANTES',
        'DETALLE_COBRO','COBROS','RESERVAS_AGENDA','MANTENIMIENTOS','MATERIALES_ORDEN','ORDENES_TRABAJO',
        'EQUIPOS_INSTALADOS','CONTRATOS_MANTENIMIENTO','DETALLE_VENTA','VENTAS','ITEMS_PROFORMA','PROFORMAS',
        'PAGOS_PROVEEDOR','DETALLE_COMPRA','COMPRAS','AJUSTES_INVENTARIO','SEDES_CLIENTE',
        'PRODUCTOS','CATEGORIAS','CLIENTES','PROVEEDORES','HISTORIAL_CAMBIOS','VENTA_AUDITORIA','LOG_ERRORES',
        'SERIES_COMPROBANTE'))) LOOP
        EXECUTE IMMEDIATE 'DELETE FROM ' || t.tabla;
    END LOOP;

    FOR i IN 1..5 LOOP
        INSERT INTO categorias(nombre, descripcion)
        VALUES (categorias(i), 'Categoria de demostracion local') RETURNING id INTO categorias_ids(i);
        v_ruc := ruc_demo(2099999000+i);
        INSERT INTO clientes(tipo, nombreorazon_social, ruc, direccion, agente_retencion)
        VALUES ('EMPRESA', nombres_clientes(i), v_ruc,
                'Av. Demostracion ' || (100+i) || ', Lima', FALSE) RETURNING id INTO clientes_ids(i);
        v_ruc := ruc_demo(2099999100+i);
        INSERT INTO proveedores(tipo, nombre_o_razon_social, ruc, direccion, contacto_vendedor)
        VALUES ('EMPRESA', nombres_proveedores(i), v_ruc,
                'Jr. Proveedores ' || (200+i) || ', Lima', 'Contacto Demo ' || i) RETURNING id INTO proveedores_ids(i);
        INSERT INTO sedes_cliente(cliente_id, nombre, direccion, contacto)
        VALUES (clientes_ids(i), 'Sede principal', 'Av. Demostracion ' || (100+i) || ', Lima',
                'Responsable Demo ' || i) RETURNING id INTO sedes_ids(i);
        INSERT INTO productos(nombre, descripcion, sector, costo_base, margen_porcentaje, moneda,
                stock_actual, stock_minimo, visible_en_catalogo, categoria_id, clasificacion_detraccion)
        VALUES (productos_nombres(i), 'DEMO: producto o servicio para pruebas',
                CASE i WHEN 1 THEN 'CLIMATIZACION' WHEN 2 THEN 'SERVICIO' WHEN 3 THEN 'SERVICIO' ELSE 'MATERIAL_SERVICIO' END,
                CASE i WHEN 1 THEN 1500 WHEN 2 THEN 150 WHEN 3 THEN 350 WHEN 4 THEN 30 ELSE 10 END,
                CASE i WHEN 4 THEN 25 WHEN 5 THEN 30 ELSE 20 END, 'SOLES', 0, 5, TRUE, categorias_ids(i),
                CASE i WHEN 2 THEN 'MANTENIMIENTO_REPARACION' WHEN 3 THEN 'DEMAS_SERVICIOS' END)
        RETURNING id INTO productos_ids(i);
    END LOOP;

    FOR i IN 1..5 LOOP
        -- Compras: dos materiales, precio de compra con IGV, pago parcial y saldo pendiente.
        v_fecha := hoy - 160 + i * 10;
        INSERT INTO compras(fecha_compra, moneda, numero_factura, total, proveedor_id, tipo_pago,
                dias_credito, fecha_vencimiento, estado_pago, registrado_por, subtotal, igv)
        VALUES (v_fecha, 'SOLES', 'DEMO-'||LPAD(i,5,'0'), 1062, proveedores_ids(i), 'CREDITO',
                30, v_fecha+30, 'PENDIENTE', 'lisandro', 900, 162) RETURNING id INTO v_compra;
        INSERT INTO detalle_compra(cantidad, precio_unitario_compra, compra_id, producto_id)
        VALUES (20,35.40,v_compra,productos_ids(4));
        INSERT INTO detalle_compra(cantidad, precio_unitario_compra, compra_id, producto_id)
        VALUES (30,11.80,v_compra,productos_ids(5));
        INSERT INTO pagos_proveedor(compra_id, fecha_pago, monto, metodo, numero_operacion,
                observacion, estado, registrado_por, fecha_registro)
        VALUES(v_compra,v_fecha+2,300,'TRANSFERENCIA','DEMO-PROV-'||i,'Pago parcial de demostracion',
                'VIGENTE','lisandro',CAST(v_fecha+2 AS TIMESTAMP));
        UPDATE productos SET stock_actual = stock_actual + 20, ultimo_costo_compra = 30,
                fecha_ultima_compra = v_fecha WHERE id = productos_ids(4);
        UPDATE productos SET stock_actual = stock_actual + 30, ultimo_costo_compra = 10,
                fecha_ultima_compra = v_fecha WHERE id = productos_ids(5);

        INSERT INTO proformas(estado, fecha_emision, fecha_vencimiento, moneda, cliente_id, tipo_cambio,
                enviado_correo, enviado_whatsapp, condiciones_pago)
        VALUES (CASE i WHEN 1 THEN 'PENDIENTE' WHEN 2 THEN 'ACEPTADA' WHEN 3 THEN 'RECHAZADA'
                WHEN 4 THEN 'VENCIDA' ELSE 'CONVERTIDA' END,
                CASE WHEN i=4 THEN hoy-30 ELSE hoy-10 END,
                CASE WHEN i=4 THEN hoy-15 ELSE hoy+5 END, 'SOLES', clientes_ids(i), 3.75, FALSE, FALSE,
                CASE WHEN i IN (1,2) THEN '60 % de adelanto y 40 % al entregar.' ELSE 'Credito a 30 dias desde la factura.' END)
        RETURNING id INTO proformas_ids(i);
        INSERT INTO items_proforma(cantidad,precio_unitario,proforma_id,producto_id,descuento_porcentaje,precio_catalogo)
        VALUES(1,1800,proformas_ids(i),productos_ids(1),0,1800);

        -- Cinco ventas activas, cada una con cobro parcial. La quinta procede de la proforma.
        v_fecha := hoy - 120 + i * 5;
        INSERT INTO ventas(fecha_venta,moneda,tipo_cambio,tipo_pago,dias_credito,fecha_limite_pago,
                estado_pago,estado,cliente_id,proforma_id,vendedor_nombre,tipo_deduccion)
        VALUES(v_fecha,'SOLES',3.75,'CREDITO',30,v_fecha+30,'PENDIENTE','REGISTRADA',clientes_ids(i),
                CASE WHEN i=5 THEN proformas_ids(i) END,'lisandro','NINGUNA') RETURNING id INTO ventas_ids(i);
        INSERT INTO detalle_venta(cantidad,precio_unitario,producto_id,venta_id,descuento_porcentaje,costo_unitario,descripcion)
        VALUES(1,1800,productos_ids(1),ventas_ids(i),0,1500,productos_nombres(1));
        IF i=5 THEN UPDATE proformas SET venta_id=ventas_ids(i), fecha_emision=v_fecha-5,
            fecha_vencimiento=v_fecha+10 WHERE id=proformas_ids(i); END IF;
        factura(ventas_ids(i),clientes_ids(i),i,v_fecha,2124,v_comp);
        INSERT INTO cobros(fecha_cobro,metodo_pago,moneda,monto_total,referencia,cliente_id,estado,registrado_por,notas)
        VALUES(v_fecha+2,'TRANSFERENCIA','SOLES',500,'DEMO-COBRO-'||i,clientes_ids(i),'VIGENTE','lisandro',
                'Cobro parcial: quedan S/ 1624 pendientes') RETURNING id INTO v_pago;
        INSERT INTO detalle_cobro(monto,cobro_id,venta_id) VALUES(500,v_pago,ventas_ids(i));

        -- Cinco anulaciones pagadas al contado: nota de credito y devolucion pendiente por cada una.
        INSERT INTO ventas(fecha_venta,moneda,tipo_cambio,tipo_pago,estado_pago,estado,cliente_id,vendedor_nombre,tipo_deduccion)
        VALUES(hoy-15,'SOLES',3.75,'CONTADO','PAGADO','REGISTRADA',clientes_ids(i),'lisandro','NINGUNA')
        RETURNING id INTO v_venta;
        INSERT INTO detalle_venta(cantidad,precio_unitario,producto_id,venta_id,descuento_porcentaje,costo_unitario,descripcion)
        VALUES(1,37.50,productos_ids(4),v_venta,0,30,productos_nombres(4));
        factura(v_venta,clientes_ids(i),i+5,hoy-15,44.25,v_comp);
        UPDATE ventas SET estado='ANULADA',fecha_anulacion=hoy-14,anulado_por='lisandro',
                motivo_anulacion='DEMO: el cliente cancelo el pedido' WHERE id=v_venta;
        INSERT INTO notas_credito(estado_sunat,fecha_emision,motivo,numero,serie,total,comprobante_id,
                sunat_codigo,sunat_mensaje,sunat_intentos,sunat_ultimo_intento)
        VALUES('ACEPTADO',hoy-14,'DEMO: anulacion de la operacion',LPAD(i,8,'0'),'FC01',44.25,v_comp,
                0,'DEMO: sin envio real',1,CAST(hoy-14 AS TIMESTAMP)) RETURNING id INTO v_nota;
        INSERT INTO envios_sunat(nota_credito_id,intento,fecha,resultado,codigo,mensaje,ambiente)
        VALUES(v_nota,1,CAST(hoy-14 AS TIMESTAMP),'ACEPTADO',0,'DEMO: sin envio real','SIMULADO');
        INSERT INTO devoluciones(venta_id,cliente_id,moneda,monto,estado,fecha_anulacion,notas)
        VALUES(v_venta,clientes_ids(i),'SOLES',44.25,'PENDIENTE',hoy-14,'DEMO: devolucion por registrar');

        -- Equipos instalados y contratos: cuatro visitas anuales, no cinco.
        INSERT INTO equipos_instalados(cliente_id,sede_id,producto_id,numero_serie,ubicacion,
                fecha_instalacion,meses_garantia,frecuencia_meses,estado,venta_id,notas)
        VALUES(clientes_ids(i),sedes_ids(i),productos_ids(1),'DEMO-EQUIPO-'||i,'Area principal',
                v_fecha+3,12,NULL,'ACTIVO',ventas_ids(i),'Mantenimiento cubierto por contrato, sin rutina duplicada')
        RETURNING id INTO v_equipo;
        v_inicio := hoy-85+i;
        INSERT INTO contratos_mantenimiento(cliente_id,sede_id,fecha_inicio,fecha_fin,frecuencia_meses,
                monto_visita,moneda,descripcion,estado,registrado_por)
        VALUES(clientes_ids(i),sedes_ids(i),v_inicio,ADD_MONTHS(v_inicio,12),3,600,'SOLES',
                'DEMO: cuatro visitas trimestrales por anio a S/ 600 sin IGV cada visita','VIGENTE','lisandro')
        RETURNING id INTO v_contrato;
        FOR j IN 0..3 LOOP
            INSERT INTO mantenimientos(cliente_id,contrato_id,tecnico,fecha_programada,estado,
                    alerta_enviada,fecha_realizada,notas)
            VALUES(clientes_ids(i),v_contrato,CASE WHEN j IN (0,1) THEN 'Tecnico Demo '||i END,
                    ADD_MONTHS(v_inicio,3*j),CASE WHEN j=0 THEN 'REALIZADO' ELSE 'PENDIENTE' END,
                    TRUE,CASE WHEN j=0 THEN v_inicio END,
                    'DEMO: visita '||(j+1)||' de 4. Alertas externas omitidas en esta carga')
            RETURNING id INTO v_mantenimiento;
            IF j IN (0,1) THEN
                INSERT INTO ordenes_trabajo(tipo,estado,cliente_id,sede_id,equipo_id,mantenimiento_id,
                        fecha_programada,hora_inicio,duracion_minutos,tecnico,direccion,descripcion,
                        fecha_realizada,trabajo_realizado,recibido_por,registrado_por,cerrado_por)
                VALUES('MANTENIMIENTO',CASE WHEN j=0 THEN 'REALIZADA' ELSE 'PROGRAMADA' END,
                        clientes_ids(i),sedes_ids(i),v_equipo,v_mantenimiento,ADD_MONTHS(v_inicio,3*j),
                        '09:00',120,'Tecnico Demo '||i,'Av. Demostracion '||(100+i)||', Lima',
                        'DEMO: mantenimiento trimestral contratado',CASE WHEN j=0 THEN v_inicio END,
                        CASE WHEN j=0 THEN 'Limpieza de filtros y serpentines; revision electrica y de presiones.' END,
                        CASE WHEN j=0 THEN 'Responsable Demo '||i END,'lisandro',CASE WHEN j=0 THEN 'lisandro' END)
                RETURNING id INTO v_orden;
                UPDATE mantenimientos SET orden_trabajo_id=v_orden WHERE id=v_mantenimiento;
                INSERT INTO reservas_agenda(clave)
                VALUES(TO_CHAR(ADD_MONTHS(v_inicio,3*j),'YYYY-MM-DD')||'|tecnico demo '||i);
                IF j=0 THEN
                    INSERT INTO materiales_orden(orden_id,producto_id,cantidad,costo_unitario,moneda)
                    VALUES(v_orden,productos_ids(4),2,30,'SOLES');
                    UPDATE productos SET stock_actual=stock_actual-2 WHERE id=productos_ids(4);
                END IF;
            END IF;
        END LOOP;

    END LOOP;

    FOR i IN 1..5 LOOP
        SELECT stock_actual INTO v_stock FROM productos WHERE id=productos_ids(5);
        INSERT INTO ajustes_inventario(producto_id,fecha,tipo,cantidad,motivo,observacion,
                stock_anterior,stock_nuevo,registrado_por,fecha_registro)
        VALUES(productos_ids(5),hoy,'ENTRADA',2,'CONTEO_FISICO','DEMO: sobrante detectado en conteo',
                v_stock,v_stock+2,'lisandro',CAST(hoy AS TIMESTAMP));
        UPDATE productos SET stock_actual=stock_actual+2 WHERE id=productos_ids(5);
    END LOOP;

    INSERT INTO series_comprobante(serie,ultimo_numero) VALUES('F001',10);
    INSERT INTO series_comprobante(serie,ultimo_numero) VALUES('B001',0);
    INSERT INTO series_comprobante(serie,ultimo_numero) VALUES('FC01',5);
    -- La carga no es una actividad de usuarios: sin errores ni entregas de correo ficticias.
    DELETE FROM venta_auditoria;
    DELETE FROM historial_cambios;
    DELETE FROM log_errores;

    FOR t IN (SELECT COLUMN_VALUE tabla FROM TABLE(SYS.ODCIVARCHAR2LIST(
        'CLIENTES','PROVEEDORES','CATEGORIAS','PRODUCTOS','SEDES_CLIENTE','PROFORMAS',
        'COMPRAS','PAGOS_PROVEEDOR','COBROS','EQUIPOS_INSTALADOS','CONTRATOS_MANTENIMIENTO',
        'AJUSTES_INVENTARIO','NOTAS_CREDITO','DEVOLUCIONES'))) LOOP
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM '||t.tabla INTO n;
        comprobar(n,5,t.tabla);
    END LOOP;
    SELECT COUNT(*) INTO n FROM ventas; comprobar(n,10,'VENTAS (5 activas + 5 anuladas)');
    SELECT COUNT(*) INTO n FROM ordenes_trabajo; comprobar(n,10,'ORDENES (5 cerradas + 5 futuras)');
    SELECT COUNT(*) INTO n FROM mantenimientos; comprobar(n,20,'MANTENIMIENTOS (4 por contrato)');
    SELECT stock_actual INTO n FROM productos WHERE id=productos_ids(4); comprobar(n,90,'STOCK COBRE');
    SELECT stock_actual INTO n FROM productos WHERE id=productos_ids(5); comprobar(n,160,'STOCK AISLAMIENTO');
    SELECT COUNT(*) INTO n FROM mantenimientos m JOIN ordenes_trabajo o ON o.id=m.orden_trabajo_id
        WHERE m.fecha_programada<>o.fecha_programada OR m.tecnico<>o.tecnico OR o.mantenimiento_id<>m.id;
    comprobar(n,0,'DESCUADRES AGENDA / ORDEN');
    SELECT COUNT(*) INTO n FROM comprobantes WHERE ambiente_emision<>'SIMULADO';
    comprobar(n,0,'COMPROBANTES NO SIMULADOS');
    IF fin='COMMIT' THEN COMMIT; ELSE ROLLBACK; END IF;
    DBMS_OUTPUT.PUT_LINE('CARGA CINCO: '||fin||' FINALIZADO');
EXCEPTION WHEN OTHERS THEN
    ROLLBACK;
    RAISE;
END;
/
