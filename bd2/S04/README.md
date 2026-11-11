# BD2 · S4 · Optimización de consultas SQL

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 4: analizar y mejorar el plan de una consulta real del proyecto con
`EXPLAIN PLAN`, `DBMS_STATS` y una reescritura. Sin índices nuevos, que son tema de S5. Se ejecutó en la base de
práctica `sge-jd-bd2`.

| Orden | Script | Qué hace |
|---|---|---|
| 1 | [S04_01_volumen.sql](S04_01_volumen.sql) | Estadísticas con la base chica y carga de 30 000 ventas (91 029 líneas) |
| 2 | [S04_02_planes.sql](S04_02_planes.sql) | `EXPLAIN PLAN` en los tres momentos y comprobación de que el resultado no cambia |
| 3 | [S04_03_ejecucion_real.sql](S04_03_ejecucion_real.sql) | Ejecuta de verdad las dos versiones, 20 veces cada una, y mide el tiempo |
| 4 | [S04_04_plan_real.sql](S04_04_plan_real.sql) (`-Dba`) | Plan real del cursor: filas estimadas frente a filas reales y valores de las variables |

## 1. Consulta representativa

| Elemento | SGE-JD |
|---|---|
| Propósito | Reporte de **Rentabilidad**: líneas vendidas de un periodo con su venta, cliente, producto y categoría, para calcular margen por producto, categoría y técnico |
| Origen | `VentaRepository.lineasVendidas(desde, hasta)` (JPQL), que usa `RentabilidadService` |
| Tablas | `DETALLE_VENTA`, `VENTAS`, `CLIENTES`, `PRODUCTOS` y `CATEGORIAS` (LEFT JOIN): cinco tablas |
| Periodo de prueba | Octubre de 2026 |
| Problemas en la versión actual | 1) **Filtros opcionales** `(:desde IS NULL OR v.fecha_venta >= :desde)`: un mismo SQL sirve "con fecha" y "sin fecha", así que el optimizador no puede saber cuántas filas filtra. 2) **Negación** `v.estado <> 'ANULADA'` en lugar de una igualdad |

La consulta que se analiza es el SQL que genera Hibernate desde ese JPQL:

```sql
SELECT d.id, d.cantidad, d.precio_unitario, d.descuento_porcentaje, d.costo_unitario,
       v.id AS venta_id, v.fecha_venta, c.nombreorazon_social, p.nombre, cat.nombre AS categoria
FROM detalle_venta d
JOIN ventas v     ON v.id = d.venta_id
JOIN clientes c   ON c.id = v.cliente_id
JOIN productos p  ON p.id = d.producto_id
LEFT JOIN categorias cat ON cat.id = p.categoria_id
WHERE v.estado <> 'ANULADA'
  AND (:desde IS NULL OR v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD'))
  AND (:hasta IS NULL OR v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD'))
ORDER BY v.fecha_venta, v.id;
```

## 2. Volumen de prueba

La copia de desarrollo tiene 237 ventas, y con eso cualquier plan responde igual. Se cargaron **30 000 ventas**
entre el 01/01/2024 y hoy, con 1 a 5 líneas cada una, usando los clientes y productos reales. Un 3 % quedó
anulado, como en la realidad. La semilla es fija (`DBMS_RANDOM.SEED(2026)`), así que cada corrida genera lo mismo.

Antes de cargar se tomaron estadísticas con la base chica. Eso es lo que pasa en producción cuando las
estadísticas se toman al instalar y la tabla crece después:

```
TABLE_NAME     FILAS_REALES  FILAS_SEGUN_ESTADISTICAS
VENTAS                30241                       241
DETALLE_VENTA         91029                       716

ANIO  VENTAS
2024   10435
2025   10533
2026    9273
```

## 3. Los tres momentos

**Momento A: consulta de Hibernate con estadísticas viejas.** El optimizador cree que `VENTAS` tiene 241
filas y que pasan el filtro **2**. Por eso empieza por `VENTAS` y busca cada cliente por índice (NESTED LOOPS):

```
| Id  | Operation                         | Name          | Rows  | Cost (%CPU)|
|   0 | SELECT STATEMENT                  |               |     7 |    21   (5)|
|   1 |  SORT ORDER BY                    |               |     7 |    21   (5)|
|*  2 |   HASH JOIN OUTER                 |               |     7 |    20   (0)|
|*  3 |    HASH JOIN                      |               |     7 |    17   (0)|
|*  4 |     HASH JOIN                     |               |     7 |    14   (0)|
|   5 |      NESTED LOOPS                 |               |     2 |     8   (0)|
|*  7 |        TABLE ACCESS FULL          | VENTAS        |     2 |     6   (0)|
|*  8 |        INDEX UNIQUE SCAN          | SYS_C008872   |     1 |     0   (0)|
|   9 |       TABLE ACCESS BY INDEX ROWID | CLIENTES      |     1 |     1   (0)|
|  10 |      TABLE ACCESS FULL            | DETALLE_VENTA |   716 |     6   (0)|
   7 - filter((:DESDE IS NULL OR "V"."FECHA_VENTA">=TO_DATE(:DESDE,'YYYY-MM-DD')) AND
              (:HASTA IS NULL OR "V"."FECHA_VENTA"<=TO_DATE(:HASTA,'YYYY-MM-DD')) AND "V"."ESTADO"<>'ANULADA')
```

**Momento B: misma consulta después de `DBMS_STATS.GATHER_TABLE_STATS`.** Las estadísticas ya dicen 30 241 y
91 029. El plan cambia: une `CLIENTES` con un MERGE JOIN y lee `DETALLE_VENTA` completa con un HASH JOIN:

```
|   0 | SELECT STATEMENT                  |               |   834 |   252   (2)|
|   7 |      MERGE JOIN                   |               |   279 |   107   (2)|
|  11 |        TABLE ACCESS FULL          | VENTAS        |   279 |   104   (1)|
|  12 |      TABLE ACCESS FULL            | DETALLE_VENTA | 91029 |   138   (1)|
```

**Momento C: consulta reescrita con estadísticas al día.** Se aplicaron dos buenas prácticas:

```sql
WHERE v.estado = 'REGISTRADA'                              -- igualdad, no negación
  AND v.fecha_venta >= TO_DATE(:desde, 'YYYY-MM-DD')       -- condición fija, sin "IS NULL OR"
  AND v.fecha_venta <= TO_DATE(:hasta, 'YYYY-MM-DD')
```
```
|   0 | SELECT STATEMENT                  |               |   219 |   252   (2)|
|*  2 |   FILTER                          |               |       |            |
|  12 |        TABLE ACCESS FULL          | VENTAS        |    73 |   104   (1)|
|  13 |       TABLE ACCESS FULL           | DETALLE_VENTA | 91029 |   138   (1)|
   2 - filter(TO_DATE(:HASTA,'YYYY-MM-DD')>=TO_DATE(:DESDE,'YYYY-MM-DD'))
  12 - filter("V"."FECHA_VENTA">=TO_DATE(:DESDE,'YYYY-MM-DD') AND
              "V"."FECHA_VENTA"<=TO_DATE(:HASTA,'YYYY-MM-DD') AND "V"."ESTADO"='REGISTRADA')
```

Con el rango fijo, Oracle agregó por su cuenta un `FILTER` (paso 2). Si `hasta < desde`, la consulta termina sin
leer ninguna tabla. Con el `IS NULL OR` no podía hacerlo.

**Las dos versiones devuelven exactamente lo mismo**: una reescritura que cambia el resultado no es una
optimización, es un error nuevo.

```
VERSION           FILAS  UNIDADES     IMPORTE
Hibernate (A/B)    2664      8017  3668864.46
Reescrita (C)      2664      8017  3668864.46
```

## 4. Tabla comparativa

| Momento | Cambio | COST | Filas estimadas (EXPLAIN) | Filas reales | Operación relevante | Observación |
|---|---|---|---|---|---|---|
| A | Ninguno (estadísticas viejas) | 21 | **7** | 2 664 | NESTED LOOPS desde `VENTAS` (2 filas estimadas) | Estima **380 veces menos**; el costo bajo es falso: está calculado para una tabla de 241 filas |
| B | `DBMS_STATS` | 252 | 834 | 2 664 | HASH JOIN + MERGE JOIN, `DETALLE_VENTA` completa | El costo sube porque ahora es **real**; la estimación se acerca, pero sigue 3 veces abajo |
| C | `DBMS_STATS` + reescritura | 252 | 219 | 2 664 | Igual que B, más un `FILTER` de rango | Mismo costo; `EXPLAIN PLAN` estima **peor** que en B (ver hallazgo) |

**Ejecución real** (`S04_03` y `S04_04`), 20 ejecuciones de cada versión con los valores de octubre:

| Versión | Filas estimadas en `VENTAS` (real: 873) | Filas estimadas al final (real: 2 664) | Lecturas lógicas | Tiempo por ejecución |
|---|---|---|---|---|
| Hibernate | 2 539 (**2,9 veces de más**) | 7 595 (**2,9 veces de más**) | 872 | 5,66 ms |
| Reescrita | **899** (3 % de error) | **2 688** (0,9 % de error) | 872 | 5,67 ms |

```
-- Hibernate (plan real del cursor)
|*  3 |    TABLE ACCESS FULL| VENTAS        |      1 |   2539 |    873 |00:00:00.01 |   373 |
|   4 |    TABLE ACCESS FULL| DETALLE_VENTA |      1 |  91029 |  91029 |00:00:00.01 |   499 |
-- Reescrita (plan real del cursor)
|*  4 |     TABLE ACCESS FULL| VENTAS        |      1 |    899 |    873 |00:00:00.01 |   373 |
|   5 |     TABLE ACCESS FULL| DETALLE_VENTA |      1 |  91029 |  91029 |00:00:00.01 |   499 |
Peeked Binds: 1 - '2026-10-01'   2 - '2026-10-31'
```

En la ejecución real solo se pidió `d.id`. Oracle eliminó por su cuenta los JOIN con `CLIENTES`, `PRODUCTOS` y
`CATEGORIAS`, porque las claves foráneas garantizan que no cambian el resultado (*join elimination*). Lo que
se compara entonces es justo lo que difiere entre las versiones: el filtro sobre `VENTAS`.

**Conclusión honesta: hoy la reescritura no acelera nada.** Sin índice, las dos versiones leen `VENTAS` y
`DETALLE_VENTA` completas (872 bloques) y tardan lo mismo. Lo que sí cambió es que **el optimizador ahora
sabe cuántas filas vienen**, con un error del 1 % en lugar de casi el triple. Además, la condición quedó
libre para usar un índice por `FECHA_VENTA`. Con `(:desde IS NULL OR …)` ese índice no podría usarse para un
rango, porque el mismo plan tiene que servir para "sin fecha". Eso se mide en S5.

## 5. Relación con ADS y LP2

| Objeto BD2 | Decisión ADS | LP2 |
|---|---|---|
| Consulta de rentabilidad | Rendimiento (RNF-02: menos de 1 s) | `VentaRepository.lineasVendidas` y `RentabilidadService` |
| Filtros opcionales en JPQL | Mantenibilidad frente a rendimiento: un método para todos los casos | Mismo patrón en `VentaRepository.buscar` (reporte de ventas); se corrige al integrar, con dos métodos o con un `Specification` |
| `DBMS_STATS` | Operación (ADS S1: el DBA) | Producción tiene el job automático de estadísticas de Oracle; en la copia de práctica estaban `STALE_STATS = YES` desde el 08/11 |

## 6. Error o hallazgo

**Hallazgo 1: estimación 380 veces menor que la realidad.** Con las estadísticas del 08/11 (241 ventas), el
optimizador estimó **7 filas** para una consulta que devuelve **2 664**, y armó el plan para 7: NESTED LOOPS
buscando cada cliente por índice. Antes de cargar, la copia ya decía `STALE_STATS = YES` para `VENTAS`,
`DETALLE_VENTA`, `PRODUCTOS` y `CLIENTES`. `DBMS_STATS` corrigió el volumen, y el costo "subió" de 21 a 252.
Eso no es un empeoramiento: el 21 era una mentira calculada sobre una tabla 125 veces más chica.

**Hallazgo 2: `EXPLAIN PLAN` hizo ver peor la consulta mejor.** Según `EXPLAIN PLAN`, la versión reescrita
estimaba **219** filas, y la de Hibernate 834. Parecía que la reescritura empeoraba la estimación (real: 2 664).
La causa es que `EXPLAIN PLAN` **no mira el valor de las variables** (`:desde`, `:hasta`): para un rango entre
dos variables desconocidas usa un porcentaje fijo. En la ejecución real, Oracle sí mira los valores
(*bind peeking*: `'2026-10-01'`, `'2026-10-31'`, visibles en `Peeked Binds`) y la reescrita estima **2 688**,
contra 2 664 reales. La de Hibernate, en cambio, sigue en 7 595, porque con `IS NULL OR` conocer el valor no
le sirve. **Lección:** para consultas con variables, la evidencia es el plan del cursor ejecutado
(`DBMS_XPLAN.DISPLAY_CURSOR` con `GATHER_PLAN_STATISTICS`), no `EXPLAIN PLAN`. Leer ese plan necesita
privilegios sobre `V$SQL`, que el propietario no tiene. Por eso `S04_04` corre como SYSTEM, y el tema queda
para S7 y S8.

**Hallazgo 3: el backend factura solo las ventas que entran por SQL.** La prueba de ADS S4 levantó el backend
sobre esta base. La tarea `FacturacionService.emitirVentasPorEmitir()` encontró **29 092 ventas sin
comprobante** (la carga de esta sesión y las de prueba de S1 a S3) y empezó a emitirles comprobante, simulado
en esa prueba. Eso significa que una venta registrada con `SP_REGISTRAR_VENTA` recibe factura del backend en
3 minutos o menos. Es útil, pero hay que tenerlo en cuenta al integrar. Detalle en
[ADS S4](../../docs/ads/S04_arquitecturas_modernas.md).

## 7. Reflexión

¿Por qué no basta con que una consulta "corra rápido" para decir que está optimizada?

Hoy las dos versiones corren en 5,7 ms y leen los mismos 872 bloques. Medido solo por tiempo, la reescritura
no hizo nada. Pero con 30 000 ventas, 5,7 ms leyendo las tablas enteras significa que el tiempo crece en línea
con los datos: con diez años de JD serían diez veces más. Lo que cambió es que ahora el optimizador sabe
cuántas filas vienen (error del 1 % en lugar de casi el triple) y la condición puede usar un índice. Una
consulta está optimizada cuando su plan es correcto para el volumen real y seguirá siéndolo cuando crezca, y eso
solo se ve comparando filas estimadas con filas reales, no mirando el reloj.

## Para repetir esta sesión

`S04_01` borra su propia carga antes de volver a cargar. Pero si después se corrió la prueba de dos instancias
de ADS S4, las ventas cargadas ya tienen comprobantes, y la clave foránea `COMPROBANTES.VENTA_ID` impide
borrarlas. En ese caso hay que restaurar la base de práctica (ver [../README.md](../README.md)) y correr S1 a S4
en orden.
