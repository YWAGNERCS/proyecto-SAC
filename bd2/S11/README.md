# BD2 · S11 · Particionamiento y escalabilidad

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 11. El objetivo es decidir **qué** particionar según cómo se usan los datos,
comprobar con `Pstart`/`Pstop` si Oracle de verdad descarta particiones, provocar a propósito el error que lo
impide y medir `DROP PARTITION` frente a `DELETE`. Se hizo en la base de práctica, sobre las ~27 000 ventas en orden
de fecha de S10. La edición Free incluye la opción (`V$OPTION`: `Partitioning = TRUE`).

| Orden | Script | Qué hace |
|---|---|---|
| 1 | [S11_01_ventas_particionada.sql](S11_01_ventas_particionada.sql) | `VENTAS_PART`: copia de `VENTAS` por mes (INTERVAL); pruning con rango, `TRUNC` y `TO_CHAR` |
| 2 | [S11_02_variantes.sql](S11_02_variantes.sql) | Hash, List y Composite con los datos reales, en el diccionario |
| 3 | [S11_03_drop_frente_a_delete.sql](S11_03_drop_frente_a_delete.sql) | Purga de 12 meses de auditoría: `DELETE` frente a `DROP PARTITION` |
| 4 | [S11_04_drop_con_indices.sql](S11_04_drop_con_indices.sql) | El `DROP` correcto, con `UPDATE GLOBAL INDEXES` |

Todo se construye sobre **copias** (`VENTAS_PART`, `AUDITORIA_PART`…), igual que S4 y S5. Las tablas que usa el
backend no se tocan.

## 1. Tablas candidatas y estrategia, según el patrón de acceso

| Tabla | ¿Cómo crece? | ¿Cómo se consulta? | ¿Se borra? | Estrategia |
|---|---|---|---|---|
| `VENTAS` (y `DETALLE_VENTA`) | Unas 30 por día laborable; nunca se reduce | **Por periodo**: el día (Inicio), el mes (Rentabilidad, S4) y el año (reportes); S4, S5 y S10 lo mostraron | **No.** SUNAT exige conservar ventas y comprobantes por años | **RANGE mensual por `FECHA_VENTA`, con INTERVAL**: el pruning sirve; el `DROP` no aplica |
| `VENTA_AUDITORIA` (S2) | **La que más crece**: una fila por cada INSERT, UPDATE y DELETE de una venta (S5 la vio con 19 779 filas para 237 ventas, por la cobranza) | Por venta, y "qué pasó en tal periodo" | **Sí**, por antigüedad: se conserva un tiempo y después se purga | **RANGE mensual por `FECHA_EVENTO`, con INTERVAL**: la purga es un `DROP PARTITION` |
| `CLIENTES`, `PRODUCTOS` | 37 y 39 filas | Por ID | — | Ninguna: particionar tablas chicas solo agrega costo |

Con **INTERVAL**, Oracle crea la partición del mes siguiente cuando llega la primera venta de ese mes. Nadie tiene
que acordarse de crearla. En una PC sin DBA es la diferencia entre que funcione o que en enero falle con
ORA-14400 ("inserted partition key does not map to any partition").

## 2. `VENTAS_PART`: una partición por mes

```
PARTICIONES  FILAS_MINIMO  FILAS_MAXIMO  FILAS_TOTAL
         36             0           877        27200

PARTITION_NAME  POSITION  NUM_ROWS  HIGH_VALUE
P_ANTES_2024           1         0  TO_DATE(' 2024-01-01 …')
SYS_P800               2       790  TO_DATE(' 2024-02-01 …')     ← creada sola por INTERVAL
…
SYS_P790              35       793  TO_DATE(' 2026-11-01 …')     ← octubre de 2026
```

## 3. Pruning: la misma pregunta escrita de cuatro formas

```
-- Octubre 2026 con rango explícito
|   2 |   PARTITION RANGE SINGLE |              |   793 |     9 |    35 |    35 |
-- Octubre 2026 con TRUNC(fecha_venta, 'MM') = DATE '2026-10-01'
|   2 |   PARTITION RANGE ALL    |              |   272 |   234 |     1 |1048575|
-- Año 2025 con TO_CHAR(fecha_venta, 'YYYY') = '2025'
|   2 |   PARTITION RANGE ALL    |              |   272 |   234 |     1 |1048575|
-- Año 2025 con rango explícito
|   2 |   PARTITION RANGE ITERATOR|             |  9540 |    80 |    14 |    25 |
```

| Consulta | Operación | Pstart / Pstop | Particiones leídas | Costo |
|---|---|---|---|---|
| Octubre, rango | `PARTITION RANGE SINGLE` | 35 / 35 | 1 de 36 | **10** |
| Octubre, `TRUNC(…,'MM')` | `PARTITION RANGE ALL` | 1 / 1048575 | todas | **235**, 23 veces más |
| 2025, `TO_CHAR(…,'YYYY')` | `PARTITION RANGE ALL` | 1 / 1048575 | todas | **235** |
| 2025, rango | `PARTITION RANGE ITERATOR` | 14 / 25 | 12 (enero a diciembre de 2025) | **81** |

Cómo leer `Pstart`/`Pstop`:

- `SINGLE` con 35/35 significa que, **antes de ejecutar**, Oracle supo que la respuesta solo podía estar en la
  partición 35.
- `ITERATOR` con 14 a 25 significa que recorre esas 12 particiones y descarta las demás.
- `ALL` significa que las lee todas, porque ya no puede saber dónde está la respuesta.

Con `TRUNC` o `TO_CHAR` sobre la clave de partición, Oracle **no da ningún error ni aviso**: el plan corre igual,
solo que 23 veces más caro, y además estima mal (272 filas frente a 793).

## 4. Hash, List y Composite

```
HASH (cliente_id), 4 particiones          LIST (estado)                       COMPOSITE: RANGE (año) + LIST (estado)
SYS_P822  5182                            P_REGISTRADA  26379  'REGISTRADA'   P_2024  SP_ANULADA 289 · SP_OTROS 0 · SP_REGISTRADA 9224
SYS_P823  7322                            P_ANULADA       821  'ANULADA'      P_2025  SP_ANULADA 303 · SP_OTROS 0 · SP_REGISTRADA 9079
SYS_P824  7393                            P_OTROS           0  DEFAULT        P_2026  SP_ANULADA 229 · SP_OTROS 0 · SP_REGISTRADA 8076
SYS_P825  7303

"Anuladas de 2025" en la Composite:
|   2 |   PARTITION RANGE SINGLE |   303 |  7 |    2 |    2 |
|   3 |    PARTITION LIST SINGLE |   303 |  7 |  KEY |  KEY |
|   4 |     TABLE ACCESS FULL    |   303 |  7 |    5 |    5 |      ← 1 subpartición de 9

TABLE_NAME   PARTITIONING  SUBPARTITIONING  PARTITION_COUNT  INTERVAL
VENTAS_COMP  RANGE         LIST                           3
VENTAS_HASH  HASH          NONE                           4
VENTAS_LIST  LIST          NONE                           3
VENTAS_PART  RANGE         NONE                     1048575  NUMTOYMINTERVAL(1, 'MONTH')
```

| Estrategia | ¿Sirve para SGE-JD? |
|---|---|
| **Hash** por cliente | **No.** Reparte para equilibrar la carga, y ninguna consulta de JD pide un grupo de clientes. Además quedó **desparejo** (5 182 frente a ~7 300): con solo 37 clientes, el hash no tiene suficientes valores para repartir bien. Hash sirve con claves de muchos valores distintos |
| **List** por estado | Aísla el 3 % de anuladas, pero ninguna consulta pide "todas las anuladas" sin un periodo (S5) |
| **Composite** año + estado | Hace pruning doble ("anuladas de 2025" lee 1 subpartición de 9). Correcta, pero para el volumen de JD el RANGE mensual solo ya alcanza |
| **Range mensual (INTERVAL)** | **Sí**: es el patrón real, consultar por periodo |

## 5. `DROP PARTITION` frente a `DELETE`: purgar un año de auditoría

Dos gemelas con los mismos 240 000 eventos (diciembre de 2024 a noviembre de 2026, 10 000 por mes), cada una con
clave primaria y un índice por venta. Se purgan los 12 meses más viejos, **120 067 filas**:

```
A) DELETE FROM auditoria_plana WHERE fecha_evento < TIMESTAMP '2025-12-01 00:00:00';
   120067 rows deleted.     Elapsed: 00:00:01.17

B) ALTER TABLE auditoria_part DROP PARTITION … (13 particiones)
   Particiones eliminadas: 13 en 290 ms

   INDEX_NAME               PAR  STATUS
   IX_AUDITORIA_PART_VENTA  YES  N/A          ← LOCAL: sus particiones se fueron con las de la tabla
   PK_AUDITORIA_PART        NO   UNUSABLE     ← GLOBAL: apunta a filas que ya no existen
   INSERT INTO auditoria_part VALUES (…)
   ORA-01502: index 'JDREFRIGERACION.PK_AUDITORIA_PART' or partition of such index is in unusable state

C) Igual, con UPDATE GLOBAL INDEXES
   Particiones eliminadas: 13 en 240 ms (con UPDATE GLOBAL INDEXES)
   PK_AUDITORIA_PART        NO   VALID
   FILAS_QUE_QUEDAN 119933 · INSERT → 1 row created.
```

| Forma | Tiempo | Índices después | ¿Se puede seguir escribiendo? |
|---|---|---|---|
| `DELETE` | **1 170 ms** (en la primera corrida, 670 ms) | Válidos | Sí, y deja 120 000 filas de undo y redo |
| `DROP PARTITION` | **290 ms** (primera corrida: 350 ms) | La PK global **UNUSABLE** | **No**: ORA-01502 hasta reconstruirla |
| `DROP PARTITION … UPDATE GLOBAL INDEXES` | **240 ms** | Todos válidos | Sí |

**Decisión:** la purga de la auditoría se hace con `DROP PARTITION … UPDATE GLOBAL INDEXES`. Es unas **4 a 5 veces**
más rápida que el `DELETE`, casi no genera undo ni redo, y deja los índices válidos. Desde Oracle 12c, el
mantenimiento del índice global se difiere, por eso no fue más lento que el `DROP` simple. La diferencia con la
guía (allí más de 30 veces) viene del volumen y la máquina. Lo que se repite en cualquier caso es la tendencia: el
`DELETE` crece con las filas, el `DROP` no.

## 6. Relación con ADS y LP2

| BD2 | ADS | LP2 |
|---|---|---|
| RANGE mensual en ventas | Rendimiento y escalabilidad (S4: una PC durante años); retención legal de SUNAT | Sin cambios: JPA no sabe de particiones. Pero los reportes deben filtrar con rangos (S4) y nunca con `TRUNC` o `TO_CHAR` sobre la fecha |
| `DROP PARTITION … UPDATE GLOBAL INDEXES` en auditoría | Auditabilidad (S1) con un costo acotado | El trigger de S2 sigue insertando durante la purga, sin ORA-01502 |
| Hash descartado | Decisión medida, no por catálogo | — |

## 7. Error o hallazgo

**Hallazgo 1: el `DROP PARTITION` simple habría detenido las ventas.** El `DROP` de la purga dejó la clave primaria
global en `UNUSABLE`, y el siguiente `INSERT` falló con ORA-01502. En `VENTA_AUDITORIA` real, el que inserta es el
**trigger de S2, dentro de la transacción de cada venta**. Una purga mal escrita haría fallar todas las ventas de
Lucy hasta que alguien reconstruya el índice. Es el mismo efecto que el `MOVE` de S9, pero aquí lo provoca una
tarea de mantenimiento de rutina. La forma correcta (`UPDATE GLOBAL INDEXES`) no solo evita el problema: además
fue un poco más rápida.

**Hallazgo 2: Hash no reparte con pocos valores.** Se esperaban 4 particiones de unas 6 800 filas. Salió una con
5 182 y tres con ~7 300, porque la clave tiene solo 37 valores (clientes), y el hash los reparte de a grupos.

**Hallazgo 3: con INTERVAL, `Pstop` y `PARTITION_COUNT` dicen 1048575.** En `PARTITION RANGE ALL`, el plan mostró
`1 / 1048575`, y `USER_PART_TABLES` también, aunque la tabla tiene 36 particiones. Es el máximo teórico que Oracle
reserva para las particiones que INTERVAL podría crear. Leído rápido, parece un error. Para saber cuántas existen
hay que contar en `USER_TAB_PARTITIONS`.

**Error propio:** la primera corrida de S11_03 no creó las claves primarias. Había un comentario `-- índice GLOBAL`
después del `;` en la misma línea. Para SQL*Plus ese `;` ya no termina la sentencia: pegó las dos `ALTER TABLE` en
una y dio ORA-03405. Sin la PK no había índice global, y la primera medición no mostraba el hallazgo 1. Es el tercer
tropiezo de SQL*Plus con la puntuación, después del guion final de PROMPT (S1, S5) y del `DELETE …; COMMIT;` en una
línea (S10).

## 8. Reflexión

¿Por qué una función sobre la clave de partición puede apagar el pruning sin ningún error, y qué cuesta eso a
medida que la tabla crece?

Porque el pruning se decide **antes** de ejecutar, comparando el filtro con los límites de cada partición. Con
`fecha_venta >= … AND < …`, Oracle compara directamente. Con `TRUNC(fecha_venta, 'MM')`, el valor recién existe al
leer cada fila, así que no puede descartar ninguna. Como la respuesta sigue siendo correcta, no hay nada que
reportar. Hoy, con 36 meses, eso cuesta 23 veces más (235 frente a 10). Cada mes nuevo agrega una partición que la
consulta mala lee y la buena no: en diez años serían 120 particiones leídas para responder sobre un mes. Pasa lo
mismo que con `DELETE` frente a `DROP PARTITION`: lo que cuesta proporcional al total se nota cuando ya es tarde.
El `DELETE` de un año tardó 1,17 s con 240 000 eventos, y con el ritmo real de la auditoría de JD crecería cada
año. El `DROP` sigue costando lo mismo.
