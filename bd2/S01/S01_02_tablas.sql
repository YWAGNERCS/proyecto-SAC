-- ============================================================
-- BD2 · S1 · Paso 2: tablas de la operación transaccional y sus reglas en Oracle.
-- Se ejecuta como el propietario JDREFRIGERACION:
--   .\bd2\herramientas\sql.ps1 bd2\S01\S01_02_tablas.sql
--
-- Las tablas VENTAS, DETALLE_VENTA y PRODUCTOS ya existen (las creó LP2; su DDL completo está en
-- scripts/prod/esquema_jdrefrigeracion.sql). Hallazgo de S1: Oracle aceptaba stock negativo, cantidades en
-- cero y descuentos de más de 100 %; esas reglas solo las cuidaba el backend Java. Se agregan como CHECK.
-- Antes de crearlas se comprueba que ningún dato existente las rompa (si no, el ALTER fallaría con ORA-02293).
-- ============================================================
set echo on feedback on linesize 200 pagesize 100
whenever sqlerror continue

DESC VENTAS
DESC DETALLE_VENTA
DESC PRODUCTOS

-- 1) ¿Hay datos que romperían las reglas? (todo debe salir en 0)
SELECT COUNT(CASE WHEN cantidad <= 0 THEN 1 END)                         AS cantidad_mala,
       COUNT(CASE WHEN precio_unitario < 0 THEN 1 END)                   AS precio_malo,
       COUNT(CASE WHEN descuento_porcentaje NOT BETWEEN 0 AND 100 THEN 1 END) AS descuento_malo
FROM detalle_venta;

SELECT COUNT(CASE WHEN stock_actual < 0 THEN 1 END)      AS stock_negativo,
       COUNT(CASE WHEN costo_base < 0 THEN 1 END)        AS costo_negativo,
       COUNT(CASE WHEN margen_porcentaje < 0 THEN 1 END) AS margen_negativo
FROM productos;

-- 2) Las reglas críticas, ahora también en la base
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_venta_cantidad  CHECK (cantidad > 0);
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_venta_precio    CHECK (precio_unitario >= 0);
ALTER TABLE detalle_venta ADD CONSTRAINT ck_detalle_venta_descuento CHECK (descuento_porcentaje BETWEEN 0 AND 100);
ALTER TABLE productos     ADD CONSTRAINT ck_producto_stock          CHECK (stock_actual >= 0);
ALTER TABLE productos     ADD CONSTRAINT ck_producto_costo_margen   CHECK (costo_base >= 0 AND margen_porcentaje >= 0);

SELECT table_name, constraint_name, search_condition_vc
FROM user_constraints
WHERE constraint_name LIKE 'CK_DETALLE_VENTA%' OR constraint_name LIKE 'CK_PRODUCTO_%'
ORDER BY table_name, constraint_name;
exit
