# BD2 · S5 · Índices para optimización

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 5: decidir índices **midiendo**, no por intuición, sobre consultas reales del
backend, y verificar con `EXPLAIN PLAN` que el optimizador los usa. Se ejecutó en la base de práctica, sobre el
volumen de [S4](../S04/README.md).

| Orden | Script | Qué hace |
|---|---|---|
| 1 | [S05_01_volumen.sql](S05_01_volumen.sql) | Cobranza realista de las ventas a crédito (queda auditada) y 20 000 cobros |
| 2 | [S05_02_selectividad.sql](S05_02_selectividad.sql) | Selectividad de 7 columnas y de 8 predicados reales |
| 3 | [S05_03_indices.sql](S05_03_indices.sql) | Cada índice con su plan antes y después, y uno descartado |
| 4 | [S05_04_bitmap_concurrencia.ps1](S05_04_bitmap_concurrencia.ps1) (+ `S05_04a`, `S05_04b`) | Dos ventas guardadas a la vez, con Bitmap y con B-Tree |

## 1. Columnas candidatas, de consultas reales

| Columna | Consulta real que la usa |
|---|---|
| `DETALLE_VENTA.VENTA_ID` | Hibernate carga las líneas de cada venta (`WHERE venta_id = ?` o `IN (…)`). Es clave foránea **sin índice** (hallazgo de S4) |
| `VENTAS.FECHA_VENTA` | Ventas del día (Inicio), reporte de ventas (`VentaRepository.buscar`) y Rentabilidad (`lineasVendidas`, S4) |
| `COBROS.UPPER(TRIM(REFERENCIA))` | `CobroRepository.vigentesConReferencia`: "el mismo voucher no entra dos veces". Corre en **cada cobro** registrado |
| `VENTA_AUDITORIA.ACCION` | "¿Quién borró ventas?" sobre la auditoría de S2 |
| `VENTAS.ESTADO`, `VENTAS.MONEDA` | Filtros del reporte de ventas |

Volumen: 30 241 ventas, 91 029 líneas, 20 222 cobros y 19 779 filas de auditoría. Las de auditoría salen de la
cobranza simulada (8 693 ventas a crédito pasadas a PENDIENTE y 8 440 pagadas) y de los comprobantes que asignó
el backend en la prueba de ADS S4. Todas las estadísticas se tomaron antes de medir, siguiendo la lección de S4.

## 2. Selectividad medida

**Selectividad de la columna** (valores distintos / filas):

```
COLUMNA                          DISTINTOS  FILAS  SELECTIVIDAD
VENTAS.FECHA_VENTA                    1045  30241       .0346
VENTAS.ESTADO                            2  30241     .000066
VENTAS.MONEDA                            2  30241     .000066
DETALLE_VENTA.VENTA_ID               30241  91029       .3322
COBROS.REFERENCIA (tal cual)         16226  20222       .8024
COBROS.UPPER(TRIM(REFERENCIA))       16226  20222       .8024
VENTA_AUDITORIA.ACCION                   3  19779     .000152
```

**Selectividad del predicado**, es decir, lo que devuelve cada `WHERE` real. Es la que decide de verdad:

```
PREDICADO                                             FILAS  PORCENTAJE
Ventas de un día (hoy): fecha_venta = TRUNC(SYSDATE)     29       .096
Rentabilidad de octubre 2026 (S4)                       873      2.887
Líneas de una venta: venta_id = 20000                     1      .0011
Voucher: UPPER(TRIM(referencia)) = 'OP-01012345'          1      .0049
Auditoría: accion = 'DELETE'                              6        .03
Ventas registradas: estado = 'REGISTRADA'             29323      96.96
Ventas anuladas: estado = 'ANULADA'                     918       3.04
Ventas en dólares: moneda = 'DOLARES'                    25        .08

ESCRITOS_DISTINTO (vouchers con minúsculas o espacios de más): 3183
```

| Columna | Decisión | Por qué, según la medición |
|---|---|---|
| `DETALLE_VENTA.VENTA_ID` | **B-Tree** | Cada predicado trae 1 a 5 filas de 91 029 |
| `VENTAS.FECHA_VENTA` | **B-Tree** | La columna parece poco selectiva (0,035: es fecha sin hora), pero un día trae el 0,1 % |
| `COBROS.REFERENCIA` | **Function-Based** sobre `UPPER(TRIM(…))` | 3 183 vouchers (16 %) están escritos distinto; la consulta compara la expresión, no la columna |
| `VENTA_AUDITORIA.ACCION` | **Bitmap**, y después reemplazado (sección 4) | 3 valores en 19 779 filas: el caso típico de Bitmap |
| `VENTAS.ESTADO` | **Ninguno** | El valor que se consulta casi siempre trae el 97 % de las filas |
| `VENTAS.MONEDA` | **Ninguno** (ni siquiera se crea) | Ninguna consulta filtra solo por moneda; siempre va con fecha o cliente |

## 3. Cada índice con su plan

### 3.1 B-Tree `IX_DETALLE_VENTA_VENTA`: líneas de una venta

```
ANTES                                               DESPUÉS
|*  1 |  TABLE ACCESS FULL| DETALLE_VENTA | 3 | 138 |  |   1 |  TABLE ACCESS BY INDEX ROWID BATCHED| DETALLE_VENTA          | 3 | 2 |
                                                     |*  2 |   INDEX RANGE SCAN                 | IX_DETALLE_VENTA_VENTA | 3 | 1 |
```

El costo baja de **138 a 2**. Además, este índice también ayuda a la consulta del día: el join con
`DETALLE_VENTA` pasa a ser un NESTED LOOPS por índice. Y tiene un efecto fuera de las consultas: Oracle
recomienda indexar las claves foráneas, porque sin índice un `DELETE` o un cambio de clave en `VENTAS` bloquea
`DETALLE_VENTA` completa.

### 3.2 B-Tree `IX_VENTAS_FECHA_VENTA`: ventas del día, sí; rentabilidad del mes, no

```
UN DÍA, sin índice de fecha                         UN DÍA, con índice de fecha
|   0 | SELECT STATEMENT          |  84 | 160 |        |   0 | SELECT STATEMENT                     |  84 | 85 |
|*  4 |     TABLE ACCESS FULL | VENTAS | 28 | 104 |    |*  4 |     TABLE ACCESS BY INDEX ROWID BATCHED| VENTAS | 28 | 29 |
                                                     |*  5 |      INDEX RANGE SCAN | IX_VENTAS_FECHA_VENTA | 29 | 1 |
```

```
UN MES (octubre), con índice: el optimizador NO lo usa
|   0 | SELECT STATEMENT   |               |  2688 | 241 |
|*  1 |  HASH JOIN         |               |  2688 | 241 |
|*  2 |   TABLE ACCESS FULL| VENTAS        |   899 | 103 |
|   3 |   TABLE ACCESS FULL| DETALLE_VENTA | 91029 | 138 |

INDEX_NAME              LEAF_BLOCKS DISTINCT_KEYS CLUSTERING_FACTOR BLOQUES_TABLA FILAS_TABLA
IX_DETALLE_VENTA_VENTA          200         30241               663           499       91029
IX_VENTAS_FECHA_VENTA            81          1045             28974           373       30241
```

### 3.3 Function-Based `IX_COBROS_REFERENCIA_NORM`: voucher duplicado

```
SIN ÍNDICE                          B-TREE COMÚN sobre REFERENCIA       FUNCTION-BASED sobre UPPER(TRIM(REFERENCIA))
| TABLE ACCESS FULL | COBROS | 69 | | TABLE ACCESS FULL | COBROS | 69 | | TABLE ACCESS BY INDEX ROWID BATCHED | COBROS                    | 2 |
                                                                        | INDEX RANGE SCAN                    | IX_COBROS_REFERENCIA_NORM | 1 |
   filter(UPPER(TRIM("REFERENCIA"))='OP-01012345' …)                  access(UPPER(TRIM("REFERENCIA"))='OP-01012345')
```

El B-Tree común **no se usa**. El índice guarda `' op-01012345 '` tal cual, y la consulta busca
`'OP-01012345'`: son valores distintos. Solo el índice sobre la misma expresión que escribe la consulta
convierte el `filter` en un `access`. Costo: **69 → 2**. El B-Tree común se borró en el mismo script.

### 3.4 Bitmap `BX_VENTA_AUDITORIA_ACCION`: se usa y es eficiente

```
ANTES                                                 CON BITMAP
|*  1 |  TABLE ACCESS FULL| VENTA_AUDITORIA | 6 | 102 | |   1 |  TABLE ACCESS BY INDEX ROWID BATCHED| VENTA_AUDITORIA           | 6 | 2 |
                                                      |   2 |   BITMAP CONVERSION TO ROWIDS      |                           |   |   |
                                                      |*  3 |    BITMAP INDEX SINGLE VALUE       | BX_VENTA_AUDITORIA_ACCION |   |   |
Conteo por acción (GROUP BY accion): BITMAP INDEX FULL SCAN + BITMAP CONVERSION COUNT, costo 1, sin tocar la tabla.
```

### 3.5 El que no conviene: `VENTAS(ESTADO)`

```
estado = 'REGISTRADA' (97 %): |*  1 |  TABLE ACCESS FULL| VENTAS | 29323 | 103 |   ← ignora el índice
estado = 'ANULADA'    (3 %):  |*  2 |   INDEX RANGE SCAN | IX_VENTAS_ESTADO | 918 | 3 |   ← ¡lo usa! (costo 24 contra 103)
COLUMN_NAME  NUM_DISTINCT HISTOGRAM
ESTADO                  2 FREQUENCY
```

Se **descarta igual**, y el script lo borra:

1. La consulta habitual pide REGISTRADA (97 %) y ahí el índice no se usa.
2. "Anuladas" nunca se consulta sola: el reporte siempre lleva periodo, y para eso ya sirve
   `IX_VENTAS_FECHA_VENTA`.
3. `ESTADO` es justo la columna que cambia al anular, y cada cambio actualizaría el índice.

`MONEDA` ni se creó: 25 ventas en dólares y ninguna consulta que filtre solo por moneda.

## 4. El Bitmap con dos ventas a la vez

`VENTA_AUDITORIA` la llena el trigger de S2 **dentro de la transacción de cada venta**. Se probó con dos
sesiones: la A registra una fila de auditoría y tarda 15 s en confirmar, como una venta que todavía está
emitiendo su comprobante, y la B registra otra con la misma `ACCION` 4 s después:

```
===== Con índice BITMAP =====
Sesion A: insertó su fila de auditoría a las 19:27:03
Sesion B: índice sobre ACCION de tipo BITMAP; inserta a las 19:27:07
Sesion B: su INSERT terminó a las 19:27:18 (esperó 11.4 s)
Sesion A: terminó su transacción a las 19:27:18
===== Con índice B-Tree =====
Sesion A: insertó su fila de auditoría a las 19:27:20
Sesion B: índice sobre ACCION de tipo NORMAL; inserta a las 19:27:24
Sesion B: su INSERT terminó a las 19:27:24 (esperó 0 s)
Sesion A: terminó su transacción a las 19:27:35
```

Con Bitmap, la B quedó **bloqueada 11,4 s**, hasta el segundo exacto en que A terminó. Un Bitmap guarda un
mapa de bits por valor y, al insertar, bloquea un tramo de ese mapa que cubre muchas filas, no solo la nueva.
Con B-Tree no esperó nada. **Decisión:** en `VENTA_AUDITORIA` queda un **B-Tree** (`IX_VENTA_AUDITORIA_ACCION`),
que también encuentra los 6 DELETE sin leer la tabla, comprobado con su plan:
`INDEX RANGE SCAN | IX_VENTA_AUDITORIA_ACCION | 6 | 1`, costo total 2, igual que con el Bitmap. El Bitmap sería correcto en una copia
para reportes que se carga de noche y nadie escribe durante el día. No en una tabla que se llena con cada venta.

## 5. Índices que quedan

| Índice | Tipo | Tabla | Consulta | Costo antes → después |
|---|---|---|---|---|
| `IX_DETALLE_VENTA_VENTA` | B-Tree | `DETALLE_VENTA` | Líneas de una venta | 138 → 2 |
| `IX_VENTAS_FECHA_VENTA` | B-Tree | `VENTAS` | Ventas del día | 160 → 85 |
| `IX_COBROS_REFERENCIA_NORM` | Function-Based | `COBROS` | Voucher duplicado | 69 → 2 |
| `IX_VENTA_AUDITORIA_ACCION` | B-Tree (reemplazó al Bitmap) | `VENTA_AUDITORIA` | Auditoría por acción | 102 → 2 (igual que el Bitmap: `INDEX RANGE SCAN`, 6 filas) |
| ~~`IX_VENTAS_ESTADO`~~ | — | `VENTAS` | — | Descartado |
| ~~`IX_COBROS_REFERENCIA`~~ | — | `COBROS` | — | Descartado: no sirve para `UPPER(TRIM)` |

## 6. Relación con ADS y LP2

| Objeto BD2 | Decisión ADS | LP2 |
|---|---|---|
| `IX_DETALLE_VENTA_VENTA` | Rendimiento (RNF-02); integridad sin bloqueos de tabla | `@OneToMany` de `Venta.detalles` y `hibernate.default_batch_fetch_size=100` (`IN (…)`) |
| `IX_VENTAS_FECHA_VENTA` | Rendimiento | `VentaRepository.buscar`, `registradasEntre`, `lineasVendidas` |
| `IX_COBROS_REFERENCIA_NORM` | Integridad (el voucher no entra dos veces) a costo bajo | `CobroRepository.vigentesConReferencia`. Al integrar hay que verificar que el SQL que genera Hibernate para `UPPER(TRIM(c.referencia))` escriba la **misma** expresión; si no, Oracle no lo reconoce |
| Bitmap descartado en `VENTA_AUDITORIA` | Escalabilidad del registro de ventas | Cada venta escribe auditoría desde el trigger: un Bitmap serializaría ventas simultáneas |

## 7. Error o hallazgo

**Hallazgo 1: el Bitmap "correcto según la teoría" bloqueaba ventas.** Por selectividad (3 valores en 19 779
filas), `ACCION` era el candidato de manual a Bitmap, y el plan lo confirmaba (costo 102 → 2). Pero la prueba
con dos sesiones mostró que una segunda venta esperaba **11,4 s** a que terminara la primera. El criterio de
selectividad no basta: también importa cómo se escribe la tabla.

**Hallazgo 2: un índice que no se usa por cómo se cargaron los datos.** Para un mes (2,9 % de las filas),
el optimizador prefirió leer `VENTAS` completa. La causa está en el `CLUSTERING_FACTOR` del índice de fecha:
**28 974**, casi igual al número de filas (30 241). Quiere decir que ventas de fechas seguidas están en bloques
distintos, y leer 873 ventas por índice costaría unas 873 lecturas de bloque, más que leer los 373 bloques de la
tabla. Pasa porque la carga de S4 puso **fechas al azar** sobre IDs consecutivos. En JD las ventas entran en
orden de fecha, y el factor estaría cerca de los 373 bloques. El índice de `VENTA_ID` lo muestra: factor 663,
casi igual a los 499 bloques de su tabla. **Lección:** el volumen de prueba tiene que parecerse también en
*orden* a los datos reales, no solo en cantidad. Si no, la prueba subestima un índice que en producción sí
serviría.

**Hallazgo 3: corrección de S4.** Con el índice de fecha creado, la consulta de Hibernate con
`(:desde IS NULL OR …)` **sí** lo usó. Oracle 23 la partió en dos ramas (`VW_ORE_…`, *OR-expansion*) y usó
`INDEX RANGE SCAN` en la rama con fecha. En S4 había afirmado que eso era imposible; quedó corregido en su README.

**Error propio, repetido:** el primer `S05_03` mostró resultados de consultas en lugar de planes. Cuatro líneas
`PROMPT --- … ---` terminaban en guion, el mismo error que documenté en S1: SQL*Plus pegó la línea siguiente,
el `EXPLAIN PLAN FOR`, al PROMPT, y ejecutó el SELECT. Se corrigió reemplazando todos esos PROMPT.

## 8. Reflexión

¿Por qué crear un índice sobre cada columna que aparece en un WHERE no es una buena práctica?

Porque cada índice es una segunda copia ordenada de la columna, y Oracle la actualiza en cada INSERT, UPDATE y
DELETE, para siempre. En SGE-JD, `ESTADO` aparece en casi todos los WHERE de ventas, pero el valor que se busca
trae el 97 % de las filas y el índice no se usa. Encima, `ESTADO` es justo la columna que cambia al anular. El
Bitmap de la auditoría mostró algo peor que un costo: un índice puede cambiar el **comportamiento** del sistema
y hacer que una venta espere a otra. Un índice se justifica con una consulta real que lo usa (medido con el plan)
y con un costo de escritura aceptable (medido con la prueba de concurrencia). Lo que aparece en el WHERE es solo
el punto de partida para medir.
