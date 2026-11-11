# ADS · S14 · Decisiones arquitectónicas y trazabilidad

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 14, sobre el dossier de [S13](S13_auditoria_consistencia.md).

Ya existía un primer documento de S14, [S14_ADS_decisiones_trazabilidad.md](../S14_ADS_decisiones_trazabilidad.md) del 07/11/2026, con el ADR-006 (solo los materiales controlan stock) y la primera tabla de atributos. Se escribió **antes** de la auditoría de S13, así que este documento:
- formaliza las dos decisiones que la auditoría dejó pendientes (ADR-007 y ADR-008);
- actualiza los atributos con la evidencia de ADS S4 a S13 y BD2 S8 a S14;
- traza dos requerimientos más;
- corrige la matriz.

Lo del 07/11 que sigue vigente se referencia, no se copia.

Registro de decisiones del proyecto:

| ADR | Decisión | Estado |
|---|---|---|
| ADR-001 a ADR-005 | Arquitectura del backend, Spring Modulith, versión de Spring Boot, JWT y Keycloak | Del curso LP2 |
| ADR-S1-01, ADR-S4-01 | Envío directo a SUNAT; una sola instancia | ADS S1 y S4 |
| ADR-006 | Solo los materiales controlan stock, con una regla en el dominio | Aceptada e implementada (07/11) |
| **ADR-007** | **El abono lo crea `Cobro.abonar()`** | **Implementada el 11/11/2026** (sección 1, al final) |
| **ADR-008** | **`VentaAnuladaEvent` lleva los datos que sus oyentes necesitan** | **Implementada el 11/11/2026, con 8 campos** (sección 2, al final) |

## 1. ADR-007 · El abono lo crea `Cobro`, con `abonar(venta, monto)`

**Estado:** aceptada el 10/11/2026 e implementada el 11/11/2026. Origen: hallazgo H3 de S13.

### Contexto

Cuatro fuentes responden distinto a "¿quién crea el abono de un cobro?". Cada una tenía sentido sola, y nadie registró que la siguiente contradecía a la anterior:

| Fuente | Quién crea el abono | Cómo se llama |
|---|---|---|
| S7 (clases) | Lo crea otro; `Cobro.agregarDetalle(d)` lo recibe | `DetalleCobro`, `montoTotal` |
| S8 (capas) | El **mapper**: `toAbono(AbonoRequest, VentaParaCobro) → Abono`; `Cobro.agregarAbono(Abono)` lo recibe | `Abono`, `importe` |
| S10 (GRASP) | **`Cobro`**: `abonar(VentaParaCobro, monto)` lo crea y suma el total (Creator + Information Expert) | `DetalleCobro`, `montoTotal` |
| Código (hoy) | El **servicio**: `cobro.agregarDetalle(new DetalleCobro(venta, monto))` y, después del bucle, `cobro.setMontoTotal(total)` (`CobranzaService.java:167` y `:171`) | `DetalleCobro`, `montoTotal` |

Consecuencia práctica:
- Si alguien implementa S8 al pie de la letra, `Cobro` sigue anémico y su total se puede fijar con un *setter* que no cuadre con sus abonos.
- Si implementa S10, el `toAbono` de S8 sobra.
- Los dos diseños están aprobados en el dossier.

### Decisión

1. **`Cobro` crea sus abonos**: `public Abono abonar(VentaParaCobro venta, BigDecimal monto)` valida que el monto sea positivo y que la moneda coincida, crea el abono con `ventaId`, `documentoVenta` y `fechaVenta`, lo agrega y suma el total.
2. **El total no tiene *setter***: solo cambia dentro de `abonar()`. Así se cumple el invariante "total = Σ abonos" de S7, que hoy depende de que el servicio haga la suma bien.
3. **El mapper solo traduce**: `AbonoRequest` → `(ventaId, monto)`. `toAbono` desaparece de S8.
4. **Un solo nombre, `Abono`**, el término del negocio ("el abono a una venta", glosario de S6), en el diseño y en el código. La tabla sigue siendo `DETALLE_COBRO`, y el cambio de nombre es solo de la clase (`@Table(name = "detalle_cobro")`).
5. **El saldo sigue en el servicio:** necesita lo ya cobrado de otras transacciones (`CobroRepository.totalCobradoDeVenta`), y eso no está dentro del agregado.

### Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que el mapper cree el abono (S8) | El mapper es de la capa web. Las reglas del abono (positivo, misma moneda) quedarían repartidas entre el mapper y el servicio, y la entidad no podría proteger su total |
| Dejarlo en el servicio (lo actual) | Funciona y está probado (`CobranzaServiceTest`, 9 pruebas), pero el total depende de un `setMontoTotal` externo, y cualquier otro camino que cree cobros (una importación, una devolución) tendría que repetir la suma |
| Una fábrica aparte (`AbonoFactory`) | Indirección sin beneficio: el único que contiene y registra abonos es `Cobro` |

### Consecuencias

- El invariante del total queda en un solo lugar, y se prueba con una prueba unitaria de `Cobro` **sin base de datos**.
- `CobranzaService.registrar` se acorta: valida contra el saldo y llama a `abonar`.
- S8 (mapper y nombre), S9 (secuencia: `agregarAbono` → `abonar`) y S7 (`DetalleCobro` → `Abono`) se actualizan en el dossier al implementar. No se reescriben hoy.
- Riesgo: `Abono` tiene hoy una relación `@ManyToOne` con la entidad `Venta` (brecha B1 de S8). El ADR-007 no la resuelve: `abonar` recibe `VentaParaCobro` y guarda el `ventaId`, lo que exige hacer B1 primero o al mismo tiempo.

**Verificación contra el código, cuando se implemente:**

```bash
grep -rn "new DetalleCobro\|new Abono\|setMontoTotal" src/main/java
```

Debe aparecer solo dentro de `Cobro.java`. **Hoy aparece en `CobranzaService.java:167` y `:171`.** `CobranzaServiceTest` debe seguir 9/9.

## 2. ADR-008 · `VentaAnuladaEvent` lleva los datos que sus oyentes necesitan

**Estado:** aceptada el 10/11/2026 e implementada el 11/11/2026, con una corrección (abajo). Origen: hallazgos H1 y H5 de S13 y el rediseño de S11.

### Contexto

- Hoy el evento es `VentaAnuladaEvent(Long ventaId)` y tiene **tres** oyentes (S13, H1).
- Como solo trae el ID, `DevolucionService.alAnularVenta` (línea 48) **llama de vuelta** a `ventaService.obtenerPorIdPublico` para leer cliente, moneda y fecha de anulación.
- S11 diseñó un evento más rico `(ventaId, clienteId, moneda, fechaAnulacion, documento)`, pero S13 encontró que **omite el motivo**. El consumidor que el mismo S11 anuncia (resumen semanal de anulaciones "por motivo") tendría que volver a llamar a ventas.
- Además, la RTM decía que el stock se devolvía "por el evento", y no es así (sección 5). Hay que dejar escrito qué viaja por el evento y qué no.

### Decisión

1. El evento queda así:

   ```java
   VentaAnuladaEvent(Long ventaId, Long clienteId, Moneda moneda, LocalDate fechaAnulacion,
                     String documento, String motivo)
   ```

   Cada campo sale de un atributo real (S13, sección 4). `documento` se obtiene del puerto `ComprobantesDeVenta` y puede ser `null` si la venta nunca tuvo comprobante.
2. **Los oyentes no llaman de vuelta** a `ventas` para leer la venta anulada. Lo que es propio de su módulo, como lo pagado en cobranza o el comprobante en facturación, lo leen de su propio módulo.
3. **El stock NO viaja por el evento:** se devuelve con la fachada `ProductoService.sumarStock`, síncrona y dentro de la misma transacción, como hoy. Un oyente puede fallar o, en el futuro, ser asíncrono. El stock tiene que quedar devuelto sí o sí, junto con la anulación.

### Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Solo `ventaId` (lo actual) | Cada oyente nuevo agrega una llamada de vuelta y una dependencia de lectura hacia `ventas`. El día que los oyentes sean asíncronos (S11), leerían la venta en un momento distinto al de la anulación |
| Mandar la entidad `Venta` | Es la fuga de abstracción de S3: el oyente podría modificarla, y cualquier campo nuevo de la tabla viaja a otros módulos |
| Copia completa de la venta, con líneas | Ningún oyente usa las líneas. El stock, que sí las necesita, va por la fachada (punto 3) |
| Devolver el stock con un oyente en `catalogo` | Haría la devolución tan frágil como el oyente más débil. Hoy la anulación y el stock son todo o nada (`FlujoCompletoEndToEndTest`: 10 → 7 → 10) |

### Consecuencias

- `DevolucionService` deja de depender de `VentaService` para la anulación. Le queda `cobranzaService.pagadoDe`, que es suyo.
- El resumen semanal por motivo es posible sin tocar `ventas`.
- El evento pasa a ser un **contrato**: quitarle un campo rompe a los oyentes. Se documenta en el `record` y se cubre con una prueba de cada oyente que use solo los datos del evento.

**Verificación contra el código, cuando se implemente:**

```bash
grep -n "obtenerPorIdPublico" src/main/java/com/bomerp/cobranza/devolucion/service/DevolucionService.java
```

Hoy la encuentra en la línea 48; después no debe encontrar nada. `VentaAnuladaEvent.java` debe tener los seis campos. `FlujoCompletoEndToEndTest` debe seguir en verde (stock, visitas, devolución, 409).

## 2b. Implementación de los dos ADR (11/11/2026)

**ADR-007, cómo quedó:**
- **Verificación del ADR:** `grep "new Abono|new DetalleCobro|setMontoTotal"` solo encuentra `new Abono` dentro de `Cobro.java`.
- **La clase:** `DetalleCobro` se llama ahora `Abono`. La tabla sigue siendo `DETALLE_COBRO`, así que no hubo migración.
- **El cobro:** `Cobro.abonar(venta, monto)` valida que el monto sea positivo y que la moneda coincida, crea el abono y suma `montoTotal`, que ya no tiene *setter*.
- **El abono:** no tiene constructor público, para que solo lo cree su cobro.
- **Diferencia con el diseño:** `abonar` recibe la entidad `Venta`, no el `VentaParaCobro` de S8. Ese record y quitar la entidad del abono (brecha B1) siguen pendientes, y van juntos.
- **Sigue en el servicio:** `Cobro.anular()` (brecha B4).

**ADR-008, la corrección que apareció al implementarlo.** Los 6 campos del diseño no alcanzaban:
- La devolución de una venta **al contado** no tiene abonos en cobranza; lo pagado es el total de la venta, si estaba pagada.
- Con solo 6 campos, `DevolucionService` habría tenido que seguir consultando la venta.
- Por eso el evento quedó con **8 campos**: `(ventaId, clienteId, moneda, fechaAnulacion, documento, motivo, total, pagada)`. Los dos nuevos salen de datos reales de la venta (`calcularTotal()` y `estadoPago`), como exige la regla de S13.

Cómo quedó:
- **Sin llamada de vuelta:** `DevolucionService` ya no depende de `VentaService`. Arma la venta y el cliente como referencias JPA, sin consultarlos, con el id que trae el evento.
- **Número de comprobante:** lo pone `VentaService` con el puerto `ComprobantesDeVenta`, resuelto al anular para no crear un ciclo entre beans.
- **Stock:** sigue devolviéndose por fachada antes del evento, como fija el punto 3.

**Pruebas:**
- Todas las del backend contra la copia de desarrollo: **228/0** como el dueño y **228/0** como `JD_APP`.
- `FlujoCompletoEndToEndTest` cubre la anulación con stock, visitas, devolución y el 409 de una segunda anulación.

## 3. Atributos de calidad: decisión inicial frente a evidencia real

La tabla del 07/11 tenía cinco atributos. Aquí se completa con lo medido después, en los dos cursos. "Antes" es lo que se declaró en S1 (RNF).

| Atributo | Decisión inicial (S1) | Evidencia real | ¿Se cumple? |
|---|---|---|---|
| **Seguridad** (RNF-05) | JWT y autorización por rol | 07/11: Keycloak con PKCE, 14 de 14 casos en `05_matriz.ps1`. **Nuevo:** <br>• BD2 S8: el backend corrió **217 ejecuciones de prueba como `JD_APP`**, un usuario sin privilegios directos (solo roles derivados del código), con el mismo resultado que como dueño del esquema; las 2 fallas se deben al trigger de BD2 S2 y no a permisos. <br>• BD2 S9: la auditoría unificada registró el DELETE denegado (código 2004) y la clave incorrecta (1017) | Sí. Falta aplicar `JD_APP` en desarrollo y producción |
| **Mantenibilidad / modularidad** (RNF-01) | Monolito modular, un módulo por paquete | `ModularityTests` en verde; 15 paquetes, 13 de negocio (S13, H2); 201 métodos de prueba (199 `@Test` + 2 parametrizados). **Pero:** <br>• `VentaService` tiene 631 líneas y 25 métodos usados por 6 módulos (ADS S3). <br>• `CobranzaService` inyecta `FacturacionService` en lugar del puerto `ComprobantesDeVenta` (ADS S10). <br>• S13 encontró 5 inconsistencias entre documentos | Parcial: los límites entre módulos se cumplen y se verifican; dentro de `ventas` y `cobranza` hay deuda registrada (ADR-007 y ADR-008) |
| **Rendimiento** (RNF-02) | Endpoints bajo 1000 ms | 07/11: pantallas principales de 27 a 642 ms; listas completas `/ventas` 1079 ms y `/proformas` 1426 ms. **Nuevo, en base:** <br>• BD2 S10: índice de fecha usado (costo 251 → 175) y compuesto `(cliente_id, fecha_venta)`. <br>• BD2 S14: el reporte de rentabilidad (5 tablas, 27 200 ventas) tarda 110 ms en frío y 31 ms en caliente, con 100 % de aciertos en caché | Parcial: la base responde; las dos listas completas no se volvieron a medir y siguen pendientes |
| **Fiabilidad / integridad transaccional** (RF-11, RF-17) | Stock atómico | 07/11: todo o nada (10 → 7 → 10). **Nuevo:** <br>• BD2 S14: bloqueo real cobranza contra anulación. Con un `UPDATE` simple, la anulación esperó ~42 s y se aplicó sin mezclarse con el cobro revertido. Con la espera del backend (`WAIT 10`), se rindió a los **10,03 s** (ORA-00054), que el manejador convierte en 409. <br>• ADS S4: con dos instancias, las tareas programadas duplicaron el 50 % del trabajo, y por eso ADR-S4-01 fija una instancia | Sí, con una instancia |
| **Recuperabilidad** (RNF-08, nuevo en esta tabla) | Respaldo diario con Data Pump | BD2 S13, en la base de práctica: ARCHIVELOG, RMAN nivel 0 (28 s) y nivel 1 (12 s), PITR al segundo anterior a un `UPDATE` sin `WHERE` con **RTO de 33 s** y sin perder el cambio legítimo. **Pero:** la imagen `23-slim` de desarrollo y producción no trae RMAN, el respaldo automático no está programado y la copia fuera de la PC espera la carpeta de JD | Parcial: probado en práctica, no en producción |
| **Interoperabilidad** (RNF-03, RF-10) | API REST JSON | 07/11: SUNAT beta aceptó facturas, boletas y notas de crédito con CDR. ADS S11: capa anticorrupción real (`OseClient`), Circuit Breaker diseñado | Sí (producción espera el certificado) |

## 4. Requerimientos trazados de punta a punta

Los cinco eslabones (requerimiento → arquitectura → base de datos → backend → prueba) están en la [RTM](../RTM.md), sección 3, para cuatro requerimientos:

| Requerimiento | Trazado | Eslabones pendientes, registrados |
|---|---|---|
| RF-11 · Descuento de stock atómico | 07/11 (corregido el 10/11, sección 5) | Ninguno |
| RF-14 · Rutina de mantenimiento | 07/11 | Ninguno |
| **RF-17 · Anular venta** | **10/11** | Trigger y auditoría de BD2 S2 solo en práctica; 2 pruebas por adaptar al trigger (BD2 S8); ADR-008 |
| **RF-20 · Cobranza con abonos** | **10/11** | Índice `UX_COBRO_VOUCHER_VIGENTE` (ADS S9); prueba de `Cobro.abonar()` (ADR-007); prueba del voucher simultáneo |

RF-17 y RF-20 se eligieron porque son los que tocan los dos ADR nuevos: cada ADR queda conectado con un requerimiento y una prueba.

## 5. Error o hallazgo: la matriz decía que el stock volvía por el evento

Es una inconsistencia **distinta de las de S13**, entre la matriz y el código:

| Fuente | Qué decía |
|---|---|
| RTM, RF-17 (arquitectura) | "`ventas` → `catalogo`, `postventa`, `facturacion`, `cobranza` por `VentaAnuladaEvent`" |
| RTM, traza de RF-11 (backend) | "al anular, `VentaAnuladaEvent` lo devuelve" |
| **Código** | `VentaService.anular` llama a `productoService.sumarStock(devueltas)` en la **línea 518**, dentro de la transacción y **antes** de publicar el evento (línea 525). `grep sumarStock` solo lo encuentra ahí y en `CompraService`. **Ningún oyente de `catalogo` escucha `VentaAnuladaEvent`** |

- El código está bien y la matriz estaba mal. Si alguien "arreglaba" el código para que coincidiera con la matriz, moviendo la devolución a un oyente en `catalogo`, el stock pasaba a depender de que ese oyente no fallara. Y si el oyente se volvía asíncrono (S11), la devolución quedaba fuera de la transacción de la anulación.
- **Corrección (10/11/2026):** la RTM ya dice "fachada, misma transacción", con la nota *corregido*. Para que no vuelva a pasar, el punto 3 del ADR-008 lo deja como decisión explícita.

## 6. Matriz de trazabilidad final

[RTM.md](../RTM.md), actualizada al 10/11/2026: RF-01 a RF-35 y RNF-01 a RNF-09, con la columna de requerimiento y, para cada uno, arquitectura, base de datos, API, pantalla, prueba y estado.

Cambios de esta sesión:
- RF-17 y la traza de RF-11, corregidas (sección 5).
- RF-17 y RF-20, trazados de punta a punta.
- ADR-007 y ADR-008 en los pendientes registrados.

## 7. Reflexión

- La decisión que nunca registré fue la de la sección 5: devolver el stock por fachada y en la misma transacción, no por evento. Estaba bien en el código, pero el único documento que la mencionaba decía lo contrario.
- El Ariane 5 explotó en 1996 por reusar el software de referencia inercial del Ariane 4 sin revisarlo. Una conversión de 64 a 16 bits era segura **en el contexto** del Ariane 4, cuya velocidad horizontal nunca la desbordaba. Esa suposición no estaba escrita junto a la decisión, y el Ariane 5, más rápido, la rompió a los ~37 s del despegue.
- Aquí el contexto que hace válida la decisión es que la anulación sea una sola transacción síncrona. S11 ya propone oyentes asíncronos. Si ese cambio llegaba sin el ADR-008, alguien podía mover el stock al evento siguiendo la matriz, y la anulación habría seguido "funcionando" en las pruebas mientras perdía stock en producción.
- Un ADR no solo dice qué se decidió: dice **bajo qué supuesto** sigue siendo correcto.
