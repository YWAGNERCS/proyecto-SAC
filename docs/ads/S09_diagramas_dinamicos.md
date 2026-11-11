# ADS · S9 · Diagramas dinámicos del módulo `cobranza`

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 9 (*Diagramas dinámicos UML*). Se usa el módulo diseñado en
[S8](S08_diseno_cobranza.md), y **solo con las clases que existen en su diagrama**. Las dos que faltaban se
agregaron allá primero (hallazgo 1).

## 1. ¿Qué operación necesita un diagrama dinámico?

Las cuatro señales de la Tabla 2 de la guía, aplicadas a todas las operaciones del módulo:

| Operación | Bucle | Condiciones combinadas | Colaboración entre módulos | Transaccional | ¿Diagrama dinámico? |
|---|---|---|---|---|---|
| `porCobrar(clienteId)` | Sí (una fila por venta pendiente) | No | Sí (lee ventas) | No (solo lectura) | **No:** es una consulta; la ficha de S8 y el DTO `CuentaPorCobrarResponse` la describen completa |
| `historial(clienteId, ventaId)` | No | No | No | No | **No:** un filtro sobre el repositorio |
| `pagadoDeVenta(ventaId)` | No | No (un 404) | No | No | **No:** una suma y un error posible caben en una fila |
| **`registrar(request, usuario)`** | **Sí** (una vuelta por abono) | **Sí** (cliente, moneda, fecha, saldo, deducción, voucher) | **Sí** (clientes y ventas) | **Sí** (cobro y estado de las ventas, juntos) | **Sí: las cuatro señales y 8 reglas que pueden rechazarla** |
| `anular(id, motivo, usuario)` | Sí (una vuelta por abono) | Sí (ya anulado, motivo, venta anulada) | Sí (`volverAPendiente`) | Sí | Sí, también las cuatro. Se elige `registrar` porque tiene más reglas que fallan (8 frente a 3); `anular` se cubre con el ciclo de vida (sección 4) |
| `registrarDevolucion(id, request)` (otro agregado) | No | Sí (estado, método) | No | Sí | **No:** sus dos condiciones están en la ficha de S8 |

## 2. Diagrama de secuencia: registrar un cobro

Las clases son las de S8: `CobranzaController`, `CobranzaServiceImpl`, `ClienteService`, `CobroRepository`,
`VentasParaCobranza` (que devuelve `VentaParaCobro`), `CobroMapper` y `Cobro`. Hay un fragmento `alt` por cada
regla que puede rechazar la operación (RN1 a RN6 de S8).

```mermaid
sequenceDiagram
    autonumber
    actor Lucy as Lucy (SPA)
    participant CC as CobranzaController
    participant CS as CobranzaServiceImpl
    participant CL as ClienteService
    participant CR as CobroRepository
    participant VP as VentasParaCobranza
    participant M as CobroMapper
    participant C as Cobro

    Lucy->>CC: POST /cobranza/cobros (CobroRequest)
    alt forma inválida (@Valid: sin abonos, monto ≤ 0, sin método)
        CC-->>Lucy: 400
    end
    CC->>CS: registrar(request, usuario del token)
    CS->>CL: obtenerResumen(clienteId)
    alt cliente no existe
        CL-->>CS: RecursoNoEncontradoException
        CS-->>CC: 404
    end
    alt RN5: fecha futura
        CS-->>CC: 400
    else RN6: detracción o retención sin N° de constancia
        CS-->>CC: 400
    else RN4: una venta repetida en el cobro
        CS-->>CC: 400
    end
    CS->>CR: vigentesConReferencia(metodo, referencia)
    alt RN3: el voucher ya se registró
        CS-->>CC: 409 (indica el cobro anterior)
    end
    loop por cada ventaId, ordenados de menor a mayor
        CS->>VP: bloquearParaCobro(ventaId)
        VP-->>CS: VentaParaCobro (fila bloqueada hasta el fin de la transacción)
    end
    CS->>C: nuevo(cliente, fecha, moneda, método, referencia, usuario)
    loop por cada abono
        alt RN1: otro cliente u otra moneda que el cobro
            CS-->>CC: 400
        else RN5: fecha del cobro anterior a la venta
            CS-->>CC: 400
        end
        CS->>CR: totalCobradoDeVenta(ventaId)
        CR-->>CS: cobrado
        alt RN2: monto > total − cobrado
            CS-->>CC: 409 (saldo de la venta)
        end
        CS->>M: toAbono(abono, venta)
        M-->>CS: Abono (con documentoVenta copiado)
        CS->>C: agregarAbono(abono)
    end
    CS->>CR: save(cobro)
    loop por cada venta cuyo saldo quedó en 0
        CS->>VP: marcarPagada(ventaId)
    end
    CS->>M: toResponse(cobro)
    CS-->>CC: CobroResponse
    CC-->>Lucy: 201
```

**¿Hace falta `par`?** No. Dentro de un cobro no ocurre nada en paralelo: cada paso espera al anterior, y el
`save` y los `marcarPagada` están en la misma transacción. La concurrencia real está **entre dos cobros
distintos**. La resuelve el bloqueo ordenado de las ventas (RN8), y es un caso entre peticiones, no dentro de esta
secuencia. Eso sí, este diagrama dejó ver un hueco en esa concurrencia (hallazgo 2).

**Orden de los mensajes.** Todo lo que se puede rechazar sin tocar la base (forma, fecha, deducción, repetidas)
va **antes** del primer bloqueo. Rechazar después de bloquear las ventas haría esperar a otro usuario por una
petición que igual iba a fallar.

## 3. Diagrama de actividades, por carriles

Los rombos son los mismos `alt` de la secuencia.

```mermaid
flowchart TB
    subgraph L1["Lucy (SPA)"]
        A1([Elige cliente, ventas y montos]) --> A2[Envía el cobro]
        Z1([Ve el recibo])
        Z2([Ve el error y corrige])
    end
    subgraph L2["Backend · cobranza"]
        B1{¿Forma válida?} -->|no| E400a[400]
        B1 -->|sí| B2{¿Cliente existe?}
        B2 -->|no| E404[404]
        B2 -->|sí| B3{¿Fecha no futura,<br/>constancia si es deducción,<br/>sin ventas repetidas?}
        B3 -->|no| E400b[400]
        B3 -->|sí| B4{¿Voucher nuevo?}
        B4 -->|no| E409a[409]
        B4 -->|sí| B6[Crea el cobro]
        B6 --> B7{¿Mismo cliente y moneda,<br/>fecha ≥ la de la venta?}
        B7 -->|no| E400c[400]
        B7 -->|sí| B8{¿Monto ≤ saldo?}
        B8 -->|no| E409b[409]
        B8 -->|sí| B9[Agrega el abono]
        B9 --> B10{¿Quedan abonos?}
        B10 -->|sí| B7
        B10 -->|no| B11[Guarda el cobro]
    end
    subgraph L3["Módulo ventas"]
        V1[Bloquea las ventas en orden de ID]
        V2[Marca PAGADO las de saldo 0]
    end
    A2 --> B1
    B4 -->|sí| V1 --> B6
    B11 --> V2 --> Z1
    E400a & E404 & E400b & E409a & E400c & E409b --> Z2
```

| Rombo | `alt` de la secuencia | Regla |
|---|---|---|
| ¿Forma válida? | forma inválida | `@Valid` |
| ¿Cliente existe? | cliente no existe | 404 |
| ¿Fecha, constancia, repetidas? | RN5 / RN6 / RN4 | 400 |
| ¿Voucher nuevo? | RN3 | 409 |
| ¿Mismo cliente y moneda, fecha ≥ venta? | RN1 / RN5 | 400 |
| ¿Monto ≤ saldo? | RN2 | 409 |

## 4. ¿Necesita un diagrama de ciclo de vida?

**`Cobro`: no.** Tiene dos estados (VIGENTE y ANULADO) y una sola transición, que no se deshace. Su condición
(motivo de 5 caracteres o más, que la venta no esté anulada) ya está en la ficha de S8 y en `CK_COBRO_ANULADO`. Un
diagrama no diría nada que una fila de tabla no diga.

**`Venta.estadoPago`: sí.** Es el estado que cobranza **cambia en otro módulo**. Su continuidad depende de
operaciones que ocurren en momentos distintos (vender, cobrar, anular un cobro), y ninguna de ellas lo ve completo:

```mermaid
stateDiagram-v2
    [*] --> PAGADO : venta al contado
    [*] --> PENDIENTE : venta a crédito
    PENDIENTE --> PAGADO : un cobro deja el saldo en 0 (registrar, RN7)
    PAGADO --> PENDIENTE : se anula un cobro que la había pagado (anular, RN9)
    PAGADO --> [*] : la venta se anula (queda la devolución en Cobranza)
    PENDIENTE --> [*] : la venta se anula
```

Se **quitó** una transición: el enum y la CHECK de Oracle admiten un tercer estado, **`ATRASADO`**, pero ninguna
línea del backend lo asigna (`grep ATRASADO` en `src/main`: solo aparece la declaración del enum). El atraso se
**calcula** cada vez a partir de `fechaLimitePago` (`diasAtraso` en `CuentaPorCobrarResponse`). Dibujar
`PENDIENTE → ATRASADO` habría sido modelar una regla que no existe (hallazgo 3).

## 5. Interacción entre componentes (nivel C3)

Solo módulos completos, y solo los mensajes que cruzan un límite:

```mermaid
sequenceDiagram
    participant SPA as SPA
    participant COB as cobranza
    participant CLI as clientes
    participant VEN as ventas
    SPA->>COB: POST /cobranza/cobros
    COB->>CLI: obtenerResumen(clienteId)
    loop por cada venta
        COB->>VEN: bloquearParaCobro(ventaId)
        VEN-->>COB: VentaParaCobro
    end
    loop por cada venta con saldo 0
        COB->>VEN: marcarPagada(ventaId)
    end
    COB-->>SPA: 201 CobroResponse
```

Desaparecen `CobroRepository`, `CobroMapper` y `Cobro`: son internos de cobranza. Lo que queda es el contrato
entre módulos: dos operaciones de `VentasParaCobranza` y una de `ClienteService`. Si mañana `ventas` cambia por
dentro, este diagrama no cambia.

## 6. Diagrama de comunicación

La misma colaboración, con los mensajes numerados (los abonos se repiten por venta: `5.n`, `6.n`):

```mermaid
flowchart LR
    Lucy((Lucy))
    CC[CobranzaController]
    CS[CobranzaServiceImpl]
    CL[ClienteService]
    CR[CobroRepository]
    VP[VentasParaCobranza]
    M[CobroMapper]
    C[Cobro]
    Lucy -- "1: registrar(CobroRequest)" --> CC
    CC -- "2: registrar(request, usuario)" --> CS
    CS -- "3: obtenerResumen(clienteId)" --> CL
    CS -- "4: vigentesConReferencia(metodo, ref) · 7.n: totalCobradoDeVenta(ventaId) · 10: save(cobro)" --> CR
    CS -- "5.n: bloquearParaCobro(ventaId) · 11.n: marcarPagada(ventaId)" --> VP
    CS -- "6: nuevo(...) · 9.n: agregarAbono(abono)" --> C
    CS -- "8.n: toAbono(abono, venta) · 12: toResponse(cobro)" --> M
```

## 7. Matriz de consistencia

Cada mensaje se cruza con la ficha de su clase en S8, más las operaciones que quedaron sin diagrama.

| # | Mensaje | Clase (S8) | ¿Está en su ficha? | Observación |
|---|---|---|---|---|
| 1 | `registrar(CobroRequest)` | `CobranzaController` | ✓ | 201 y `@Valid` |
| 2 | `registrar(request, usuario)` | `CobranzaService` | ✓ | El usuario sale del token, no del Request |
| 3 | `obtenerResumen(clienteId)` | `ClienteService` | ✓ | |
| 4 | `vigentesConReferencia(metodo, ref)` | `CobroRepository` | ✓ | Ver hallazgo 2: sin bloqueo |
| 5 | `bloquearParaCobro(ventaId)` → `VentaParaCobro` | `VentasParaCobranza` | ✓ **después de corregir S8** | `VentaParaCobro` no estaba en el diagrama de S8 (hallazgo 1) |
| 6 | `nuevo(...)` | `Cobro` | ✓ | Constructor de fábrica |
| 7 | `totalCobradoDeVenta(ventaId)` | `CobroRepository` | ✓ | Base del saldo (RN2) |
| 8 | `toAbono(abono, venta)` | `CobroMapper` | ✓ | Copia `documentoVenta` |
| 9 | `agregarAbono(abono)` | `Cobro` | ✓ | |
| 10 | `save(cobro)` | `CobroRepository` | ✓ | Spring Data |
| 11 | `marcarPagada(ventaId)` | `VentasParaCobranza` | ✓ | RN7, misma transacción |
| 12 | `toResponse(cobro)` | `CobroMapper` | ✓ | |
| — | `saldo(ventaId)` | `VentasParaCobranza` (tabla de refinamiento de S8) | ✗ **inconsistencia** | Estaba en la tabla y no en el diagrama. **Resuelto:** el saldo lo calcula cobranza (mensajes 5 y 7); se quitó de la tabla de S8 |
| — | `porCobrar`, `historial`, `pagadoDeVenta` | `CobranzaService` | ✓ | Sin diagrama (sección 1): consultas |
| — | `anular(id, motivo, usuario)` | `CobranzaService` | ✓ | Sin secuencia; su efecto en otro módulo está en el ciclo de vida (`PAGADO → PENDIENTE`) |
| — | `volverAPendiente(ventaId)` | `VentasParaCobranza` | ✓ | Solo lo usa `anular` |
| — | `registrarDevolucion` | `DevolucionService` | ✓ | Otro agregado; sin diagrama |
| — | `EstadoPago.ATRASADO` | `Venta` (módulo ventas) | ✗ **inconsistencia** | Declarado y admitido por la CHECK, nunca asignado (hallazgo 3) |

## 8. Error o hallazgo

**Hallazgo 1: la secuencia necesitaba una clase que el diagrama de S8 no tenía.** Al dibujar el mensaje 5,
`bloquearParaCobro` tenía que devolver algo con el total, el cliente, la moneda y el estado de la venta. En S8 ese
tipo aparecía nombrado en la ficha del mapper (`toAbono(AbonoRequest, VentaParaCobro)`), pero **no como clase** en
el diagrama. Además, la tabla de refinamiento le atribuía a `VentasParaCobranza` una operación `saldo` que el
diagrama no tenía. Siguiendo la regla de la guía, no se inventó nada dentro de la secuencia: se volvió a S8, se
agregó el record `VentaParaCobro` y se corrigió la tabla. El saldo lo calcula cobranza, porque solo ella conoce los
abonos.

**Hallazgo 2: el orden de los mensajes dejó ver una condición de carrera.** El mensaje 4
(`vigentesConReferencia`) comprueba que el voucher no exista **con una consulta, sin bloqueo**, y el bloqueo
(mensaje 5) es sobre las **ventas**, no sobre el voucher. Si dos personas registran a la vez el mismo N° de
operación para **ventas distintas**, las dos consultas ven "no existe", ninguna espera a la otra, y entran dos cobros
con el mismo voucher. El código actual hace lo mismo (`CobranzaService.registrar`: primero la consulta y después el
bloqueo de las ventas). En la base no hay nada que lo impida: el índice de BD2 S5 sobre
`UPPER(TRIM(referencia))` no es único. **Corrección propuesta**, con el mismo patrón que ya protege a los
comprobantes (`UX_COMPROBANTE_VIGENTE_VENTA`):

```sql
CREATE UNIQUE INDEX ux_cobro_voucher_vigente ON cobros (
    CASE WHEN estado = 'VIGENTE' AND referencia IS NOT NULL THEN metodo_pago END,
    CASE WHEN estado = 'VIGENTE' AND referencia IS NOT NULL THEN UPPER(TRIM(referencia)) END);
```

La consulta del mensaje 4 se queda, para dar un mensaje claro al usuario. El índice garantiza la regla aunque dos
peticiones lleguen juntas. El diagrama de clases de S8 no podía mostrar esto, porque ahí la regla RN3 parecía
completa.

**Hallazgo 3: una transición sin regla real.** El primer borrador del ciclo de vida de `estadoPago` tenía
`PENDIENTE → ATRASADO` "cuando vence la fecha límite". Al buscar quién hace esa transición, no la hace nadie:
`ATRASADO` solo existe en la declaración del enum y en la CHECK de `VENTAS`. El atraso se calcula al consultar.
Se quitó del diagrama. Queda como inconsistencia en la matriz: o se elimina el valor, o se le da una regla y una
tarea que lo asigne.

## 9. Reflexión

¿Qué decisión de orden o de continuidad de estado no se veía en el diagrama de clases y apareció recién en la
secuencia o en el ciclo de vida? Relación con el caso del vehículo autónomo de Uber.

En el diagrama de clases de S8, la regla "el voucher no entra dos veces" parecía resuelta: una consulta en el
repositorio y un 409. Solo al poner los mensajes en orden se vio que esa comprobación ocurre antes del bloqueo y
sobre otra cosa (las ventas), y que dos peticiones simultáneas la pasan las dos. En el caso de Uber, el sistema
detectó el obstáculo varios segundos antes, pero cada vez que cambiaba de clasificación descartaba la historia de
lo que había visto, y la decisión de frenar llegó tarde. Cada componente estaba "bien" por separado: el problema
estaba en el orden y en la continuidad del estado entre un momento y el siguiente. Lo mismo pasa con `estadoPago`
de la venta: lo cambian tres operaciones en momentos distintos, y solo el diagrama de estados mostró que una de sus
transiciones, `ATRASADO`, no la ejecutaba nadie.
