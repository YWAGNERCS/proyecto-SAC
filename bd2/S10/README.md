# BD2 · S10 · Optimización del rendimiento

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 10. Se verifica con evidencia, sin suponer nada:

- si las estadísticas están vigentes;
- si los índices de S5 se usan de verdad;
- si AWR está disponible en esta instancia;
- cómo se obtiene el mismo diagnóstico sin AWR;
- si un índice compuesto se justifica.

Se hizo en la base de práctica, con la seguridad de S8 y el almacenamiento de S9.

| Orden | Script | Se ejecuta como |
|---|---|---|
| 0 | [S10_00_pausar_auditoria.sql](S10_00_pausar_auditoria.sql) / [S10_00_reanudar_auditoria.sql](S10_00_reanudar_auditoria.sql) | SYSTEM: la política de S9 no debe registrar la carga de prueba |
| 1 | [S10_01_volumen_ordenado.sql](S10_01_volumen_ordenado.sql) | JDREFRIGERACION: unas 27 000 ventas **en orden de fecha** y los índices de S5 |
| 2 | [S10_02_estadisticas_y_planes.sql](S10_02_estadisticas_y_planes.sql) | JDREFRIGERACION |
| 3 | [S10_03b_ejecutar_consulta.sql](S10_03b_ejecutar_consulta.sql) y después [S10_03_awr_y_vsql.sql](S10_03_awr_y_vsql.sql) | JDREFRIGERACION / SYSDBA |
| 4 | [S10_04_indice_compuesto.sql](S10_04_indice_compuesto.sql) | JDREFRIGERACION |

## 1. Punto de partida: una carga que se parece a JD

En S5 la carga de S4 tenía las fechas **al azar** sobre IDs consecutivos. El índice de fecha quedó con un
`CLUSTERING_FACTOR` de 28 974, casi una fila por bloque, y el optimizador no lo usaba para un mes. En JD las ventas
entran en el orden en que ocurren. Por eso esta vez se cargó **día por día**, del 01/01/2024 a hoy, con 20 a 40
ventas por día laborable:

```
Ventas cargadas: 26942          (total en la tabla: 27 200 · líneas: 81 531)
Index created.  IX_VENTAS_FECHA_VENTA   (en TS_SGEJD_VENTAS)
Index created.  IX_DETALLE_VENTA_VENTA  (en TS_SGEJD_VENTAS)
```

## 2. ¿Estadísticas vigentes?

```
TABLE_NAME     FILAS_SEGUN_ESTADISTICAS  FILAS_REALES  ANALIZADA    STALE_S
DETALLE_VENTA                       733         81531  10/11 20:43  YES
VENTAS                              258         27200  10/11 20:43  YES
```

Las estadísticas creían que había **258 ventas**, cuando había 27 200: 105 veces menos. Es el mismo error de S4.
Se corrigieron con `DBMS_STATS.GATHER_TABLE_STATS(USER, …, cascade => TRUE)`. Con `cascade`, también se recalculan
las estadísticas de los índices de la tabla:

```
DETALLE_VENTA   81531  10/11 20:44  NO
VENTAS          27200  10/11 20:44  NO
```

## 3. ¿Se usan los índices de S5? Ahora sí

```
INDEX_NAME              LEAF_BLOCKS  DISTINCT_KEYS  CLUSTERING_FACTOR  BLOQUES_TABLA  FILAS_TABLA
IX_DETALLE_VENTA_VENTA          178          27312                596            499        81531
IX_VENTAS_FECHA_VENTA            73            897                730            373        27200   ← en S5: 28 974
```

Con las ventas en orden de fecha, el factor de agrupamiento bajó de **28 974 a 730**, cerca de los 373 bloques
de la tabla. Las ventas de un mismo mes están en bloques vecinos. La consulta de Rentabilidad de S4 (versión
reescrita, octubre de 2026), que en S5 ignoraba el índice, ahora lo usa:

```
| Id  | Operation                                 | Name                  | Rows  | Cost |
|   0 | SELECT STATEMENT                          |                       |  1606 |  175 |
|*  9 |       TABLE ACCESS BY INDEX ROWID BATCHED | VENTAS                |   817 |   27 |
|* 10 |        INDEX RANGE SCAN                   | IX_VENTAS_FECHA_VENTA |   842 |    4 |
|  11 |      TABLE ACCESS FULL                    | DETALLE_VENTA         | 81531 |  138 |

STATEMENT_ID  COST     (forzando FULL sobre VENTAS y DETALLE_VENTA con un hint)
MES            175
FULL           251
```

El optimizador eligió el índice porque cuesta 175 frente a 251, y no porque exista. `DETALLE_VENTA` se sigue
leyendo completa: para unirla con 817 ventas, un HASH JOIN sobre la tabla entera le resulta más barato que 817
búsquedas por `IX_DETALLE_VENTA_VENTA`.

## 4. AWR: intento real

```
NAME                             VALUE
statistics_level                 TYPICAL
control_management_pack_access   DIAGNOSTIC+TUNING

SNAPSHOTS_PREVIOS (en FREEPDB1)   0

BEGIN DBMS_WORKLOAD_REPOSITORY.CREATE_SNAPSHOT(); END;   →  PL/SQL procedure successfully completed.

SNAP_ID  INICIO          FIN
      2  10/11 20:44:42  10/11 20:45:20      (segunda corrida)
      1  10/11 20:43:08  10/11 20:44:42
```

**El comando funcionó**, y la instancia declara los paquetes de Diagnóstico y Tuning como habilitados. Que funcione
no significa que esté permitido. AWR pertenece al *Diagnostics Pack*, que se licencia aparte, y Oracle no lo bloquea
técnicamente. Usarlo en un servidor real de JD sin esa licencia sería uso no autorizado, aunque nunca dé un error.
Por eso el diagnóstico de la sección 5 se hace sin AWR.

## 5. El mismo diagnóstico con vistas `V$` (sin licencia)

**Las consultas más caras en memoria ahora**:

```
SQL_ID         EJEC  MS_TOTAL  MS_POR_EJEC  LECTURAS_POR_EJEC  TEXTO
3qkhfbf2kyvhk     4       126         31.5                401  SELECT POS+1 POS, VAL, NONNULLS, NDV, … (interna de DBMS_STATS)
1t3xkwr9xva5j     5        42         8.35                527  SELECT /*+ GATHER_PLAN_STATISTICS S10_RENTABILIDAD */ D.ID FROM DETALL…
2f002z1xcdb8a    54        16           .3                  3  SELECT VALUE FROM SYS.NLS_SESSION_PARAMETERS …
```

**Plan real de la consulta de Rentabilidad** (`DBMS_XPLAN.DISPLAY_CURSOR`, `ALLSTATS LAST`):

```
| Id  | Operation                             | Name                  | Starts | E-Rows | A-Rows | Buffers |
|   0 | SELECT STATEMENT                      |                       |      1 |        |   2251 |     485 |
|*  2 |   HASH JOIN                           |                       |      1 |   2438 |   2251 |     485 |
|*  3 |    TABLE ACCESS BY INDEX ROWID BATCHED| VENTAS                |      1 |    817 |    765 |      43 |
|*  4 |     INDEX RANGE SCAN                  | IX_VENTAS_FECHA_VENTA |      1 |    842 |    793 |       4 |
|   5 |    TABLE ACCESS FULL                  | DETALLE_VENTA         |      1 |  81531 |  81531 |     442 |
```

Responde las mismas preguntas que un reporte AWR, pero solo para lo que está en memoria ahora:

- **qué tan bien estimó:** 817 filas estimadas y 765 reales;
- **dónde está el costo:** `DETALLE_VENTA` se lleva 442 de los 485 bloques;
- **si el índice ayudó:** `VENTAS` costó 43 bloques, frente a los 373 de leer la tabla completa.

## 6. ¿Índice compuesto? Medido antes de decidir

Consulta real: el historial de **un cliente en un periodo** (`VentaRepository.buscar(estado, desde, hasta,
clienteId)`, filtro de cliente en la pantalla de Ventas).

```
COLUMNAS                   DISTINTOS  FILAS  SELECTIVIDAD
cliente_id                        37  27200       .00136
fecha_venta                      897  27200       .03298
estado                             2  27200       .00007
(cliente_id, fecha_venta)      18577  27200       .68298     ← casi única
(estado, fecha_venta)           1437  27200       .05283     ← apenas mejor que la fecha sola

INDICES_SOBRE_CLIENTE_ID: 0      ← la FK VENTAS.CLIENTE_ID no tiene ningún índice

solo cliente 12          714 filas
solo octubre             793 filas
cliente 12 y octubre      27 filas
```

| | Plan | Costo |
|---|---|---|
| **Antes** | `INDEX RANGE SCAN` sobre `IX_VENTAS_FECHA_VENTA` (842 entradas) y **filtro** `CLIENTE_ID = 12` sobre cada fila | 28 |
| **Después** de `(cliente_id, fecha_venta)` | `INDEX RANGE SCAN` sobre `IX_VENTAS_CLIENTE_FECHA` con las **dos condiciones como acceso** (22 entradas) | **12** |
| Solo el periodo, sin cliente | Sigue usando `IX_VENTAS_FECHA_VENTA` (el compuesto empieza por cliente y no sirve) | 27 |

**Decisión: se crea `IX_VENTAS_CLIENTE_FECHA (cliente_id, fecha_venta)`.** Se justifica por tres razones medidas:

1. Reduce el costo de 28 a 12 en una consulta real de la pantalla de Ventas.
2. La combinación es casi única (0,68).
3. Cubre la clave foránea `VENTAS.CLIENTE_ID`, que no tenía índice. Ese es el mismo riesgo de bloqueo que
   S5 encontró en `DETALLE_VENTA.VENTA_ID`: sin índice en la FK, borrar o cambiar un cliente bloquea `VENTAS`.

El orden de las columnas importa: va primero la de **igualdad** (cliente) y después la de **rango** (fecha). Al
revés, el índice serviría para el periodo pero no podría usar el cliente como acceso.

**Descartado: `(estado, fecha_venta)`.** Su selectividad (0,053) apenas mejora la de la fecha sola (0,033), porque
el 97 % de las ventas está REGISTRADA (S5). Sería un segundo índice para casi la misma información, y habría que
mantenerlo en cada venta y en cada anulación.

## 7. Relación con ADS y LP2

| BD2 | ADS | LP2 |
|---|---|---|
| Estadísticas al día con `cascade` | Operación (stakeholder DBA, S1) | En producción lo hace el job automático; esta copia lo tenía atrasado |
| `IX_VENTAS_FECHA_VENTA` usado de verdad | Rendimiento (RNF-02) | `lineasVendidas`, `registradasEntre` |
| `IX_VENTAS_CLIENTE_FECHA` | Rendimiento | `VentaRepository.buscar` con `clienteId`; historial del cliente |
| AWR sin licencia, diagnóstico con `V$` | Restricción de costo para JD (ADR-S4-01: una PC, sin licencias extra) | — |

## 8. Error o hallazgo

**Hallazgo 1: la lección de S5 se cumplió.** En S5 el índice de fecha no se usaba para un mes, y la causa
diagnosticada fue el orden de la carga, no el índice. Con los datos en orden de fecha, el `CLUSTERING_FACTOR` pasó de
28 974 a **730**, y el mismo índice, para la misma consulta, ahora se usa: costo 175 frente a 251. Si me hubiera
quedado con la conclusión de S5 ("el índice no sirve para un mes"), habría descartado un índice que en la
realidad de JD sí ayuda.

**Hallazgo 2: AWR funcionó, y eso no lo hace válido.** Se esperaba un posible bloqueo por licencia. No lo hubo:
`CREATE_SNAPSHOT` corrió sin error y `control_management_pack_access` dice `DIAGNOSTIC+TUNING`. Tampoco había
snapshots automáticos previos en `FREEPDB1` (0), a diferencia de lo que anticipaba la guía. El riesgo está
justamente ahí: nada técnico le avisa a quien lo usa que está consumiendo una opción de pago.

**Hallazgo 3: `V$SQL` es memoria, no historia.** La primera vez que se buscó el cursor de la consulta de
Rentabilidad en `V$SQL`, ya no estaba: `no rows selected` y `cannot fetch plan`, aunque se había ejecutado un
minuto antes. Se volvió a ejecutar y se buscó de inmediato, y entonces apareció (`1t3xkwr9xva5j`, 5 ejecuciones).
El diagnóstico con `V$` solo sirve para lo que ocurre ahora. Para comparar con ayer hace falta guardar el
resultado, que es exactamente lo que hace AWR y lo que se paga.

**Error propio, por tercera vez:** `COUNT(*)` junto a una subconsulta escalar sin agrupar da ORA-00937. Me pasó
en S5, en S6 y aquí. La forma que funciona es calcular cada conteo en su propia tabla derivada (`WITH … d1, d2, d3`)
y unirlas.

## 9. Reflexión

¿Por qué un índice que "suena razonable" puede terminar sin usarse, y qué cuesta mantenerlo de todas formas?

El optimizador no usa un índice porque exista: lo usa si, con las estadísticas reales, le sale más barato. En S5,
el índice de fecha "sonaba razonable" para un mes y no se usaba, porque los datos estaban desordenados. Hoy, con
los datos como en la realidad, sí se usa. El de `(estado, fecha_venta)` también sonaba razonable, y la medición
mostró que casi no agrega nada sobre el de fecha. Si se hubiera creado igual, Oracle lo actualizaría en cada venta
registrada y en cada anulación, para siempre, sin que ninguna consulta lo aprovechara. Ese costo nadie lo ve en un
plan, porque se paga en cada escritura. El compuesto `(cliente_id, fecha_venta)` se creó porque los números lo
justificaron: costo de 28 a 12, una combinación casi única y una FK que no tenía índice.
