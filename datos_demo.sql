-- ============================================================
-- SGE-JD · Reinicio de datos de demostración (13/10/2026)
--
-- BORRA todos los datos de negocio (ventas, proformas, compras, comprobantes,
-- notas de crédito, mantenimientos, productos, categorías, clientes, proveedores),
-- reinicia los ID en 1 y carga un catálogo de demostración.
-- NO toca USUARIOS (los accesos admin / asistente se conservan).
--
-- Proveedores y clientes: empresas reales verificadas en el padrón de SUNAT
-- (ACTIVO / HABIDO) el 13/10/2026; razón social y dirección fiscal según SUNAT.
-- No se inventan teléfonos ni correos: se completan desde la aplicación.
-- La relación comercial con JD Refrigeración es FICTICIA (datos de prueba).
-- Precios: referenciales de mercado, sin IGV.
--
-- Ejecutar desde la carpeta bomerp-backend (PowerShell):
--   Get-Content -Encoding UTF8 datos_demo.sql | docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 SGE-JD sqlplus -s jdrefrigeracion/<password>@FREEPDB1
-- (NLS_LANG evita que se pierdan las tildes y la ñ.)
-- ============================================================
SET DEFINE OFF
SET FEEDBACK OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

-- ── 1. Borrado (hijos antes que padres) ──────────────────────
DELETE FROM NOTAS_CREDITO;
DELETE FROM COMPROBANTES;
DELETE FROM MANTENIMIENTOS;
DELETE FROM DETALLE_VENTA;
DELETE FROM VENTAS;
DELETE FROM ITEMS_PROFORMA;
DELETE FROM PROFORMAS;
DELETE FROM DETALLE_COMPRA;
DELETE FROM COMPRAS;
DELETE FROM PRODUCTOS;
DELETE FROM CATEGORIAS;
DELETE FROM CLIENTES;
DELETE FROM PROVEEDORES;
COMMIT;

-- ── 2. Reinicio de los ID (columnas IDENTITY) ───────────────
ALTER TABLE NOTAS_CREDITO  MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE COMPROBANTES   MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE MANTENIMIENTOS MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE DETALLE_VENTA  MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE VENTAS         MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE ITEMS_PROFORMA MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE PROFORMAS      MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE DETALLE_COMPRA MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE COMPRAS        MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE PRODUCTOS      MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE CATEGORIAS     MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE CLIENTES       MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);
ALTER TABLE PROVEEDORES    MODIFY ID GENERATED ALWAYS AS IDENTITY (START WITH 1);

-- ── 3. Categorías ────────────────────────────────────────────
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Aire acondicionado split', 'Equipos split pared inverter para hogares y oficinas');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Cassette y piso-techo', 'Equipos comerciales de mayor capacidad para locales, clínicas y oficinas');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Refrigeración comercial', 'Unidades condensadoras, evaporadores, cámaras y vitrinas refrigeradas');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Ventilación y extracción', 'Extractores y ventiladores para ventilación forzada');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Gases refrigerantes', 'Refrigerantes y gases técnicos para carga y pruebas');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Tuberías y aislamiento', 'Tubería de cobre, aislamiento térmico, soportes y accesorios de instalación');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Herramientas e instrumentos', 'Equipos de diagnóstico, vacío y carga');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Repuestos y componentes', 'Compresores, capacitores, motores y tarjetas electrónicas');

-- ── 4. Productos ─────────────────────────────────────────────
-- Equipos (sector distinto de MATERIAL_SERVICIO): no controlan stock → stock NULL.
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado split inverter 9 000 BTU LG', 'Split pared inverter, refrigerante R32, solo frío', 'CLIMATIZACION', 1250, 30, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Aire acondicionado split'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado split inverter 12 000 BTU Daikin', 'Split pared inverter, refrigerante R32, frío/calor', 'CLIMATIZACION', 1650, 30, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Aire acondicionado split'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado split inverter 18 000 BTU Daikin', 'Split pared inverter, refrigerante R32, frío/calor', 'CLIMATIZACION', 2450, 30, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Aire acondicionado split'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado split Dual Inverter 24 000 BTU LG', 'Split pared Dual Inverter, refrigerante R410A, frío/calor', 'CLIMATIZACION', 3200, 28, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Aire acondicionado split'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado cassette inverter 36 000 BTU LG', 'Cassette 4 vías para techo falso, 220 V', 'CLIMATIZACION', 1450, 25, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Cassette y piso-techo'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado piso-techo 48 000 BTU Daikin', 'Piso-techo inverter, 220 V trifásico', 'CLIMATIZACION', 1850, 25, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Cassette y piso-techo'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aire acondicionado piso-techo 60 000 BTU Daikin', 'Piso-techo inverter, 380 V trifásico, uso comercial', 'CLIMATIZACION', 2300, 25, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Cassette y piso-techo'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Unidad condensadora 2 HP para cámara de refrigeración', 'Condensadora hermética, refrigerante R404A, 220 V', 'REFRIGERACION', 1100, 30, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Refrigeración comercial'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Evaporador para cámara de refrigeración 2 HP', 'Evaporador de techo con 2 ventiladores, deshielo natural', 'REFRIGERACION', 650, 30, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Refrigeración comercial'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Cámara frigorífica modular 3 x 3 x 2.5 m', 'Paneles de poliuretano de 10 cm, puerta batiente, sin equipo', 'REFRIGERACION', 4200, 30, 'DOLARES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Refrigeración comercial'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Vitrina refrigerada vertical de 2 puertas', 'Exhibidora de bebidas 1 000 L, puertas de vidrio', 'REFRIGERACION', 3800, 25, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Refrigeración comercial'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Extractor helicoidal de pared 12" Soler & Palau', 'Extractor para cocinas y baños, 220 V', 'VENTILACION_FORZADA', 420, 35, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Ventilación y extracción'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Extractor centrífugo en línea 6" Soler & Palau', 'Para ductos de 150 mm, bajo nivel sonoro', 'VENTILACION_FORZADA', 690, 35, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Ventilación y extracción'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Ventilador axial industrial 24"', 'Ventilador de alto caudal para naves y almacenes', 'VENTILACION_FORZADA', 1150, 30, 'SOLES', NULL, NULL, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Ventilación y extracción'));

-- Materiales y servicios (MATERIAL_SERVICIO): controlan stock.
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Gas refrigerante R410A cilindro 11.3 kg', 'Cilindro descartable para equipos split y comerciales', 'MATERIAL_SERVICIO', 480, 30, 'SOLES', 20, 5, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Gases refrigerantes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Gas refrigerante R32 cilindro 9.5 kg', 'Refrigerante de nueva generación para equipos inverter', 'MATERIAL_SERVICIO', 420, 30, 'SOLES', 15, 5, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Gases refrigerantes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Gas refrigerante R404A cilindro 10.9 kg', 'Para refrigeración comercial de media y baja temperatura', 'MATERIAL_SERVICIO', 560, 30, 'SOLES', 10, 3, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Gases refrigerantes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Nitrógeno seco cilindro 10 m³', 'Para barrido y pruebas de hermeticidad de tuberías', 'MATERIAL_SERVICIO', 180, 25, 'SOLES', 6, 2, 0, (SELECT id FROM CATEGORIAS WHERE nombre = 'Gases refrigerantes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Tubería de cobre flexible 1/4" rollo 15 m', 'Cobre deshidratado para refrigeración', 'MATERIAL_SERVICIO', 165, 35, 'SOLES', 30, 10, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Tubería de cobre flexible 3/8" rollo 15 m', 'Cobre deshidratado para refrigeración', 'MATERIAL_SERVICIO', 230, 35, 'SOLES', 30, 10, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Tubería de cobre flexible 1/2" rollo 15 m', 'Cobre deshidratado para refrigeración', 'MATERIAL_SERVICIO', 310, 35, 'SOLES', 20, 8, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Aislamiento térmico elastomérico 3/8" x 1.8 m', 'Coquilla de espuma elastomérica para tubería', 'MATERIAL_SERVICIO', 9.5, 40, 'SOLES', 200, 50, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Soporte metálico para condensadora 18 000–24 000 BTU', 'Rack de pared con pintura electrostática', 'MATERIAL_SERVICIO', 85, 40, 'SOLES', 25, 8, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Cinta vinílica para tubería 2"', 'Rollo de 15 m para acabado de líneas', 'MATERIAL_SERVICIO', 6.5, 50, 'SOLES', 150, 40, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Tuberías y aislamiento'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Bomba de vacío de 2 etapas 5 CFM', 'Para deshidratar sistemas antes de la carga', 'MATERIAL_SERVICIO', 620, 30, 'SOLES', 4, 1, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Herramientas e instrumentos'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Manómetro digital para refrigeración R410A / R32', 'Manifold digital de 2 vías con lectura de presión y temperatura', 'MATERIAL_SERVICIO', 780, 30, 'SOLES', 3, 1, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Herramientas e instrumentos'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Capacitor de marcha 35 + 5 µF 440 V', 'Capacitor dual para compresor y ventilador', 'MATERIAL_SERVICIO', 28, 50, 'SOLES', 60, 15, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Repuestos y componentes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Compresor rotativo 12 000 BTU R410A', 'Repuesto para equipos split de 12 000 BTU', 'MATERIAL_SERVICIO', 520, 30, 'SOLES', 6, 2, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Repuestos y componentes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Tarjeta electrónica universal para split', 'Kit de control con control remoto y sensores', 'MATERIAL_SERVICIO', 95, 45, 'SOLES', 15, 5, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Repuestos y componentes'));
INSERT INTO PRODUCTOS (nombre, descripcion, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo, categoria_id)
  VALUES ('Motor ventilador para condensadora 1/6 HP', 'Motor de 220 V con capacitor incluido', 'MATERIAL_SERVICIO', 180, 35, 'SOLES', 8, 3, 1, (SELECT id FROM CATEGORIAS WHERE nombre = 'Repuestos y componentes'));

-- ── 5. Proveedores (reales, verificados en SUNAT: ACTIVO / HABIDO) ──
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'DAIKIN SAEG PERU S.A.C.', '20556601152', 'AV. EL DERBY NRO 250 INT. 1501, SANTIAGO DE SURCO, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'LG ELECTRONICS PERU S.A.', '20375755344', 'AV. REPUBLICA DE COLOMBIA NRO 791 URB. CHACARILLA DE SANTA CRUZ, SAN ISIDRO, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'SOLER & PALAU PERU S.A.', '20566343631', 'AV. EL SANTUARIO NRO 1061 URB. ZARATE, SAN JUAN DE LURIGANCHO, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'DE RIGO REFRIGERACION LATAM S.A.C.', '20602092985', 'AV. CIRCUNVALACIÓN DEL CLUB G NRO 154 INT. 603, SANTIAGO DE SURCO, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'LINDE PERU S.R.L.', '20338570041', 'AV. ALFREDO BENAVIDES NRO 801 INT. PI11 URB. MIRAFLORES, MIRAFLORES, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'HOMECENTERS PERUANOS S.A.', '20536557858', 'AV. AVIACION NRO 2405, SAN BORJA, LIMA');
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, direccion) VALUES ('EMPRESA', 'REFRIGERACIONES M & M EMPRESA INDIVIDUAL DE RESPONSABILIDAD LIMITADA', '20607902063', 'APV. SAN FRANCISCO DE CAY MZA. G1 LOTE 17, SAN MARTIN DE PORRES, LIMA');

-- ── 6. Clientes (empresas reales verificadas en SUNAT; relación comercial ficticia) ──
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'SUPERMERCADOS PERUANOS SOCIEDAD ANONIMA ''O '' S.P.S.A.', '20100070970', 'CAL. MORELLI NRO 181 INT. P-2, SAN BORJA, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'CENCOSUD RETAIL PERU S.A.', '20109072177', 'CAL. AUGUSTO ANGULO NRO 130 URB. SAN ANTONIO, MIRAFLORES, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'HIPERMERCADOS TOTTUS S.A', '20508565934', 'AV. ANGAMOS ESTE NRO 1805 INT. P10, SURQUILLO, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'SAGA FALABELLA S A', '20100128056', 'AV. PASEO DE LA REPUBLICA NRO 3220 URB. JARDIN, SAN ISIDRO, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'UNIÓN DE CERVECERÍAS PERUANAS BACKUS Y JOHNSTON SOCIEDAD ANÓNIMA ABIERTA', '20100113610', 'AV. NICOLAS AYLLON NRO 3986, ATE, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'CLINICA INTERNACIONAL S A', '20100054184', 'JR. WASHINGTON NRO 1471, LIMA, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'ADMINISTRADORA CLINICA RICARDO PALMA S.A.', '20100121809', 'AV. JAVIER PRADO ESTE NRO 1066 URB. CORPAC, SAN ISIDRO, LIMA');
INSERT INTO CLIENTES (tipo, nombreorazon_social, ruc, direccion) VALUES ('EMPRESA', 'UNIVERSIDAD PERUANA UNION', '20138122256', 'CAR. CENTRAL VILLA UNION-NAÑA KM. 19, LURIGANCHO, LIMA');

COMMIT;

-- Resumen
SET FEEDBACK ON
SELECT 'CATEGORIAS: ' || COUNT(*) AS resumen FROM CATEGORIAS
UNION ALL SELECT 'PRODUCTOS: '   || COUNT(*) FROM PRODUCTOS
UNION ALL SELECT 'PROVEEDORES: ' || COUNT(*) FROM PROVEEDORES
UNION ALL SELECT 'CLIENTES: '    || COUNT(*) FROM CLIENTES
UNION ALL SELECT 'VENTAS: '      || COUNT(*) FROM VENTAS;
EXIT
