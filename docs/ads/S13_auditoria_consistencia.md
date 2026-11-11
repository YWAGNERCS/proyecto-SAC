# ADS · S13 · Auditoría de consistencia y dossier consolidado de la Unidad III

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 13. Los documentos de S1 a S11 se escribieron por separado. Aquí se cruzan entre sí y
contra el código. No se escribe nada nuevo de diseño: se verifica que todos cuenten la misma historia y se registra
lo que no cuadra. Las resoluciones que piden una decisión formal pasan a ADR en S14. **Nada se corrige en silencio**:
los documentos de origen no se tocaron.

Cada afirmación sobre el código se verificó el 10/11/2026 con `grep` sobre `src/main/java`. Se cita el archivo y la línea.

## 1. Insumos (se referencian, no se copian)

| Unidad | Qué consolida | Documentos |
|---|---|---|
| U1 (S1–S4) | Visión, C1–C3, SOLID, estilo (monolito modular) | [S01](S01_vision_arquitectonica.md), [S02](S02_modelo_c4.md), [S03](S03_solid_modulo_ventas.md), [S04](S04_arquitecturas_modernas.md) |
| U2 (S6–S11) | Dominio, clases, diseño por capas de `cobranza`, dinámicos, patrones, integración | [S06](S06_modelo_dominio.md), [S07](S07_diagrama_clases.md), [S08](S08_diseno_cobranza.md), [S09](S09_diagramas_dinamicos.md), [S10](S10_patrones_cobranza.md), [S11](S11_integracion_sunat.md) |
| Código | Lo que de verdad existe | `src/main/java/com/bomerp/`: 15 paquetes (cada uno es un módulo para Spring Modulith, `ModularityTests`) |

## 2. Arquitectura (C3, S2) frente a dominio (S6–S8)

Todas las entidades del modelo de dominio, ubicadas en la vista de componentes:

| Entidad (sesión) | Módulo | ¿Está en la C3 de S2? | Observación |
|---|---|---|---|
| Venta, DetalleVenta (S6, S7) | ventas | **Sí, real** (`VentaService`, `VentaRepository`, entidades) | — |
| Producto (+ Material, Equipo, Servicio), Categoria (S7) | catalogo | **Sí, real** (fachada `ProductoService`) | — |
| Cliente, SedeCliente (S7) | clientes | **Sí, real** (fachada `ClienteService`) | — |
| Comprobante, NotaCredito, DocumentoElectronico, EnvioSunat (S7) | facturacion | **Sí, real** (`VentasFacturacionListener`). El envío a SUNAT se declaró "se abre en S11", y S11 lo abrió (`EnvioSunatService`, `SunatDirectoClient`, `OseClient`) | — |
| TopesDescuento (S6, regla del descuento) | seguridad | **Sí, real** (`TopeDescuentoService`) | — |
| Mantenimiento (S6, recorrido) | postventa | **Sí, real** (`VentasPostventaListener`) | — |
| **Cobro, DetalleCobro / Abono, Devolucion** (S6, S7, S8) | **cobranza** | **No.** Ni real ni futuro con nombre: solo el genérico "faltan los otros 10 módulos" | **Hallazgo H1** |
| Proforma (S6) | proformas | Sí (fachada `ProformaService`) | — |
| Módulos sin entidades modeladas (servicios, compras, inventario, proveedores, notificaciones) | — | Cubiertos por "faltan los otros 10 módulos, que tendrán su propia vista C3" | No es hallazgo: S6 los dejó como candidatos, igual que BomERP con inventario y compras |

**H1. `cobranza` falta en la C3, y la C3 dibuja mal a los oyentes de `VentaAnuladaEvent`.**

S6 eligió `cobranza` como uno de los tres módulos que se modelan en profundidad. S8 lo diseñó entero. La C3 de S2,
en cambio:
- **No muestra que `cobranza` llama a `ventas`.** En el código lo hace: `CobranzaService` (líneas 43, 61, 151 y 177) usa `ventaService.listarPendientesDeCobro`, `obtenerPorIdPublico` y `marcarComoPagada`.
- **Dibuja solo dos oyentes del evento de anulación** (`EV -.-> FL` y `EV -.-> PL`). En el código hay **tres**: `DevolucionService.alAnularVenta` (cobranza, línea 47), además de facturación y postventa.
  - `DevolucionService` existe desde el 25/10/2026, según git, y S2 se escribió después. El oyente ya existía y la C3 lo omitió.
  - S11 sí lista los tres consumidores, así que la C3 y la integración se contradicen.

Resolución: opción (a) de la guía. Se agrega `cobranza` a la C3 de `ventas`:
- como **cliente de la fachada** de ventas (línea sólida hacia `VentaService`, que el rediseño de S8 reemplaza por `VentasParaCobranza`);
- como **tercer oyente** de `VentaAnuladaEvent` (`DevolucionService`).

La opción (b), dejarlo implícito, no aplica: `cobranza` ya es un módulo real con 21 clases.

**H2. "13 módulos" quiere decir dos listas distintas.**

| Documento | Qué dice | Qué lista |
|---|---|---|
| S1, S2 | "13 módulos" de negocio | Sin lista explícita |
| S6 (sección 1) | 13 candidatos | Incluye `seguridad`; **no incluye `reportes` ni `config`** |
| S8 (sección 2) | "13 módulos más `config` y `seguridad`" | El árbol incluye `reportes` ("transitorio") |
| Código | **15 paquetes** | catalogo, clientes, cobranza, compras, config, facturacion, inventario, notificaciones, postventa, proformas, proveedores, reportes, seguridad, servicios, ventas |

- El total real es 15 y cuadra con S8. Pero "los 13" de S6 y "los 13" de S8 no son los mismos: uno cuenta `seguridad` y el otro cuenta `reportes`.
- `reportes` no aparece en el panorama de S6. Tiene sentido, porque no es un concepto del negocio y S8 lo declara transitorio, pero ninguna sesión lo dice.

Resolución: el dossier usa **una sola cuenta**: 15 módulos para Spring Modulith, de los cuales 13 son de negocio. `config` y `reportes` son técnicos, y `reportes` es transitorio (S8, regla 7). `seguridad` se cuenta como de negocio (subdominio Generic, S6) porque guarda topes y permisos propios de JD.

## 3. Dominio (S7) frente a capas, dinámicos y patrones (S8–S10)

Se rastrearon cuatro operaciones por todas las sesiones que las mencionan. Una celda vacía significa que esa sesión no
tenía por qué nombrarla.

| Operación | S6 | S7 | S8 | S9 | S10 | Código | Veredicto |
|---|---|---|---|---|---|---|---|
| `Venta.calcularTotal()` | Regla "total = líneas + IGV", en `Venta.calcularTotal()` | Operación de `Venta`, "en `Venta` ✓" | `VentaParaCobro.total` la expone sin dar la entidad | Usa el saldo (`¿Monto ≤ saldo?`), sin nombrar la operación | — | `Venta.java:167`. La usan `CobranzaService` (líneas 70, 162, 249) y `FacturacionService:181` | **Consistente.** A diferencia del caso de BomERP en la guía, aquí la operación nunca se mudó al servicio |
| `Cobro.anular(motivo, usuario)` | "Se anula con motivo, no se borra" | Operación de `Cobro`; "hoy en `CobranzaService`" | En el diagrama de `Cobro`. Matriz: "`Cobro.anular()` (hoy en el servicio)", brecha **B4** | Sin secuencia; su efecto, en el ciclo de vida `PAGADO → PENDIENTE`, por `CobranzaService` | Information Expert: "el refactor mueve `abonar()` y `anular()` a la entidad" | `CobranzaService.anular`, línea 194 | **Consistente.** La mudanza al servicio está registrada en S7, S8 (B4) y S10, y todas dicen lo mismo |
| `marcarPagada` | "cuando el saldo llega a 0, la venta pasa a PAGADO", en `VentaService.marcarComoPagada` | `Venta.marcarPagada()`; "está en `VentaService`" | `VentasParaCobranza.marcarPagada(ventaId)` (interfaz nueva) | `marcarPagada(ventaId)` en la secuencia | "`VentaService` … `marcarComoPagada`" (lo real) | `VentaService.marcarComoPagada`, línea 460 | **Consistente.** El diseño usa `marcarPagada` y el código `marcarComoPagada`, y cada sesión dice cuál de los dos describe. Al implementar S8, el nombre de diseño reemplaza al real |
| **Crear y sumar el abono** | — | `Cobro.agregarDetalle(d)`, `montoTotal`, parte `DetalleCobro` | `Cobro.agregarAbono(Abono)`, `importe`, parte `Abono`; quien crea el abono es el **mapper** (`toAbono(AbonoRequest, VentaParaCobro) → Abono`) | `agregarAbono` | `Cobro.abonar(VentaParaCobro, monto)` **crea** el `DetalleCobro` y suma `montoTotal` (Creator + Information Expert) | `CobranzaService` crea `new DetalleCobro(...)` y llama `cobro.agregarDetalle` | **Hallazgo H3** |

**H3. Tres sesiones, tres respuestas a "¿quién crea el abono?".**
- **S7:** la entidad lo recibe, `agregarDetalle(d)`.
- **S8:** el **mapper** lo crea y la entidad lo recibe, `agregarAbono(Abono)`.
- **S10:** **la entidad** lo crea, con `abonar(venta, monto)`.
- **El código:** lo crea el **servicio**.

Además, la parte cambió de nombre (`DetalleCobro` → `Abono` en S8 y S9; otra vez `DetalleCobro` en S10) y el total también (`montoTotal` → `importe` → `montoTotal`).
- El cambio de nombre de S8 sí quedó registrado, en su tabla de refinamiento (fila "DetalleCobro → `Abono`").
- **Lo que nadie registró es que S10 contradice al mapper de S8.** Si un desarrollador implementa S8 tal cual, el Creator de S10 queda roto desde el primer día.

Resolución: **no se corrige el diagrama de S8 ni el de S10.** Pasa a S14 como **ADR-007**: "El abono lo crea `Cobro.abonar(VentaParaCobro, monto)`; el mapper solo traduce el request". Es la versión de S10, porque `Cobro` es quien tiene el saldo y el total.
- El ADR fija también un solo nombre para la parte. Se propone **`Abono`** (S8), porque es el término del negocio: "el abono a una venta".
- Con eso, `toAbono` del mapper de S8 queda como `toAbonoRequest`, o desaparece.

## 4. Integración (S11) frente a los atributos reales (S7, S8 y código)

Evento auditado: **`VentaAnuladaEvent`** en su versión diseñada en S11, `(ventaId, clienteId, moneda, fechaAnulacion, documento)`.
Hoy está implementado como `VentaAnuladaEvent(Long ventaId)`.

| Campo del evento | Atributo en S7 | En S8 | En el código (`Venta.java`) | Veredicto |
|---|---|---|---|---|
| `ventaId` | `Venta.id: Long` | `VentaParaCobro.id` | `id`, línea 33 | ✓ |
| `clienteId` | Asociación `Venta – Cliente` (0..* — 1) | `VentaParaCobro.clienteId` | `cliente`, línea 38 (`@ManyToOne`) | ✓ por la asociación |
| `moneda` | `Venta.moneda: Moneda` | `VentaParaCobro.moneda` | `moneda`, línea 65 | ✓ |
| `fechaAnulacion` | **No está en S7**: la lista de `Venta` tiene `anuladoPor` y `motivoAnulacion`, pero no la fecha | — | `fechaAnulacion`, línea 85; `VentaService` la fija en la línea 520 | ✓ en el código; **S7 incompleto (H4)** |
| `documento` (ej. F001-00000123) | **No es atributo de `Venta`**: es `serie` + `numero` del `Comprobante` (facturacion) | `VentaParaCobro.documento` | Se obtiene con el puerto `ComprobantesDeVenta.numeroComprobanteDeVenta(ventaId)` (`ventas/venta/service/ComprobantesDeVenta.java:12`), implementado por facturación. Así lo usa `VentaReporteService:31` | ✓ como **dato derivado**, sin ciclo entre módulos. Puede ser `null` si la venta nunca tuvo comprobante (`orElse(null)`) |
| *(falta)* `motivo` | `Venta.motivoAnulacion` | — | `motivoAnulacion`, línea 92 | **Hallazgo H5** |

**H4. A S7 le falta `fechaAnulacion`.** Está en la tabla `VENTAS`, en la entidad y en el evento diseñado. Cuando
S7 listó los atributos de `Venta` tomó los de la anulación (`anuladoPor`, `motivoAnulacion`) y se saltó la fecha.
Resolución: agregarla al diagrama de clases consolidado. Es un atributo y no una decisión, así que no necesita ADR.

**H5. El evento omite un campo que su propio consumidor necesita.** S11 anuncia un consumidor previsto: "un resumen
semanal para Lisandro (anulaciones de la semana, **por motivo**), que solo acumula los datos que trae el evento".
Pero el evento diseñado **no trae el motivo**. Ese consumidor tendría que llamar de vuelta a `ventas`, que es justo
lo que S11 prohíbe en el párrafo anterior ("el consumidor no debe llamar de vuelta").
Resolución: el evento queda `(ventaId, clienteId, moneda, fechaAnulacion, documento, motivo)`. Se registra junto con el ADR del evento en S14.

`VentaRegistradaEvent(ventaId, rutina)` también se verificó, y sus dos oyentes coinciden con la C3:
- `VentasFacturacionListener.alRegistrarVenta`, línea 35;
- `VentasPostventaListener.alRegistrarVenta`, línea 30.

Ningún tercer módulo lo escucha. **Consistente.**

## 5. Tabla de hallazgos

| # | Dónde se auditó | Hallazgo | Estado | Resolución propuesta | Destino |
|---|---|---|---|---|---|
| H1 | Arquitectura (S2) frente a dominio (S6, S8) y código | `cobranza` ausente de la C3; la C3 dibuja 2 oyentes de `VentaAnuladaEvent` y el código tiene 3 desde el 25/10 | **Inconsistencia real** | Agregar `cobranza` a la C3: cliente de la fachada de ventas y tercer oyente del evento | Dossier (C3 consolidada) |
| H2 | U1 (S1, S2) frente a U2 (S6, S8) y código | "13 módulos" cuenta `seguridad` en S6 y `reportes` en S8; el código tiene 15 | **Inconsistencia de nombres** | Una sola cuenta: 15 en Modulith, 13 de negocio, 2 técnicos (`config`, `reportes`) | Dossier |
| H3 | Dominio (S7) frente a capas (S8) y patrones (S10) | Quién crea el abono: entidad que recibe (S7), mapper (S8), entidad que crea (S10), servicio (código); y `DetalleCobro`/`Abono`, `montoTotal`/`importe` | **Inconsistencia real, no registrada** | `Cobro.abonar()` crea el abono; un solo nombre, `Abono` | **ADR-007 en S14** |
| H4 | Dominio (S7) frente a código | A `Venta` le falta `fechaAnulacion` en S7 | **Omisión** | Agregar el atributo al diagrama consolidado | Dossier |
| H5 | Integración (S11) frente a su propio consumidor | `VentaAnuladaEvent` diseñado sin `motivo`, que el resumen semanal necesita | **Inconsistencia real** | Agregar `motivo` al evento | ADR del evento en S14 |
| V1 | Dominio (S6, S7) frente a código y consumidores | `Venta.calcularTotal()` vive en la entidad en todas las sesiones y en el código | **Consistente** | Ninguna | — |
| V2 | Dominio (S7) frente a S8, S9 y S10 | `Cobro.anular()`: diseñado en la entidad, hoy en el servicio; la brecha B4 se registró en las tres sesiones | **Consistente** (pendiente conocido) | Ninguna nueva; B4 sigue abierta | — |
| V3 | Dominio (S7) frente a S8, S9 y S10 | `marcarPagada` frente a `marcarComoPagada`: cada sesión dice si describe el diseño o el código | **Consistente** | Ninguna | — |
| V4 | Integración (S11) frente a S7, S8 y código | `ventaId`, `clienteId`, `moneda`, `fechaAnulacion` y `documento` del evento salen de atributos reales; `documento` es derivado, por el puerto `ComprobantesDeVenta`, sin ciclo | **Consistente** | Ninguna | — |
| V5 | Arquitectura (S2) frente a código | `VentaRegistradaEvent`: 2 oyentes en la C3 y 2 en el código | **Consistente** | Ninguna | — |

Resultado: **5 hallazgos y 5 verificaciones confirmadas.** Ningún hallazgo se arregló editando el documento de
origen. Así se puede comprobar después que la auditoría existió.

## 6. Dossier consolidado de la Unidad III (los cuatro componentes de esta sesión)

| Componente | Fuente vigente | Lo que cambia con esta auditoría |
|---|---|---|
| **Arquitectura final** | C1 y C2 de [S02](S02_modelo_c4.md) sin cambios. C3 de `ventas` de S2, más la C3 de `cobranza` (diagrama por capas de [S08](S08_diseno_cobranza.md)), más los componentes de SUNAT de [S11](S11_integracion_sunat.md). Estilo de [S04](S04_arquitecturas_modernas.md): una instancia (ADR-S4-01) | H1: `cobranza` en la C3 de ventas, con su flecha a la fachada y su oyente. H2: 15 módulos, 13 de negocio |
| **Modelo de dominio** | [S06](S06_modelo_dominio.md) (subdominios, reglas, glosario) y [S07](S07_diagrama_clases.md) (13 clases, herencias, restricciones) | H4: `Venta.fechaAnulacion`. H3: la parte del cobro se llama `Abono`, cuando se decida el ADR-007 |
| **Catálogo UML** | [S08](S08_diseno_cobranza.md) (capas, contrato REST, objeto-relacional) y [S09](S09_diagramas_dinamicos.md) (secuencia, actividades, estados, comunicación) | H3: la secuencia de `registrar` de S9 cambia `agregarAbono` por `abonar` cuando se adopte el ADR-007 |
| **Patrones e integración** | [S10](S10_patrones_cobranza.md) (GoF, GRASP, Protected Variations) y [S11](S11_integracion_sunat.md) (capa anticorrupción, Circuit Breaker, Saga) | H5: `VentaAnuladaEvent` con `motivo` |
| ADR | S14 | ADR-007 (H3) y el ADR del evento (H5). El documento de S14 se escribió el 07/11/2026, antes de esta auditoría: se le agregan |
| Trazabilidad | S14 | Sin cambios aquí |

## 7. Error o hallazgo

El más serio es **H3**, porque es el único que habría producido código malo sin que nadie lo notara:
- S8 y S10 estaban bien cada uno por su lado. S8 pensaba en capas (el mapper traduce y crea) y S10 en responsabilidades (quien tiene el dato crea).
- Escritos con días de distancia, ninguno leyó al otro.
- Si se implementa S8, la entidad sigue anémica. Si se implementa S10, el mapper de S8 sobra.

H1 muestra otra cosa: la C3 se dibujó desde lo que S2 quería mostrar (ventas y sus vecinos) y no desde el código. Un oyente que ya existía quedó fuera.

## 8. Reflexión

No había vuelto a abrir la C3 de S2 desde que la entregué. Al compararla con el código, le faltaba un módulo entero
que S6 y S8 consideraban central. Es el síntoma de Nygard: cada documento era correcto en su fecha, pero nadie tenía
a la vista dos decisiones juntas, como el mapper de S8 frente al Creator de S10. Sin esta auditoría, en S14 habría
escrito un ADR sobre cobranza citando S8 y otro desarrollador habría implementado S10, y los dos con razón. Las cinco
filas "Consistente" importan igual: muestran que la auditoría se hizo contra el código línea por línea y no solo
buscando errores.
