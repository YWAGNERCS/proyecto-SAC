# ADS · S11 · Integración empresarial: SGE-JD y SUNAT

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 11 (*Integración y sistemas empresariales*). El sistema externo no es inventado:
es **SUNAT**, con su servicio `billService` (SOAP, comprobantes UBL 2.1 firmados), que SGE-JD ya usa en el ambiente
beta. Se marca qué está **implementado** hoy, qué falta (**hueco**) y qué se **diseña**.

## 1. Capa anticorrupción frente a SUNAT

SUNAT habla otro idioma: XML UBL 2.1 firmado y comprimido en ZIP, sobres SOAP, códigos numéricos de respuesta
(0, 1032, 1033, 2xxx, 4xxx…) y un CDR, que es otro XML dentro de otro ZIP. Nada de eso debe llegar al dominio de
SGE-JD.

| Lado SUNAT (formato ajeno) | Traducción | Lado SGE-JD (formato propio) |
|---|---|---|
| `sendBill(fileName, contentFile)` con ZIP base64 dentro de un sobre SOAP | `SunatDirectoClient` + `SunatSoapClient` (implementación) | `OseClient.emitir(Comprobante)` (puerto, **interfaz propia**) |
| XML UBL 2.1 firmado (`Invoice`, `cbc:ID`, `ds:Signature`…) | Generador UBL y firma con el certificado | `Comprobante` con su `serie`, `numero` y `total` |
| CDR (ZIP con `ApplicationResponse`) y `faultcode` SOAP | Lectura del CDR y clasificación de códigos (`ClasificacionCodigosSunatTest`, 20 pruebas) | `RespuestaOse(Resultado, codigo, mensaje, xml, cdr)` |
| 1032 "ya rechazado o anulado", 1033 "ya registrado", 0100 a 1999 excepción (SUNAT no lo registra), 2000 a 3999 rechazo con CDR, 4000 en adelante observación… | `Resultado` (enum): `ACEPTADO`, `OBSERVADO`, `RECHAZADO`, `REINTENTAR`, `POR_CONFIRMAR`, `CREDENCIALES` | `Comprobante.EstadoSunat` (PENDIENTE, ACEPTADO, OBSERVADO, RECHAZADO) |
| URL de producción o de beta | `AmbienteSunat.validar(ambiente, url)`: solo las dos direcciones oficiales | `Comprobante.AmbienteEmision` (PRODUCCION, BETA, SIMULADO), fijo una vez aceptado |

**Implementado.** El dominio solo conoce `OseClient` y `RespuestaOse`. Ningún código de ventas ni de cobranza ve un
`faultcode` ni un `cbc:` de UBL. La prueba es que existe un segundo adaptador, `NubefactClientSimulado`, que cumple
el mismo puerto sin hablar con SUNAT, y el resto del sistema no nota la diferencia.

## 2. Circuit Breaker del envío a SUNAT

**Qué hay hoy** (`EnvioSunatService` y `SunatSoapClient`):

| Mecanismo | Valor real | ¿Es un Circuit Breaker? |
|---|---|---|
| Tiempo máximo para conectar | 10 s (`connectTimeout`) | No: corta una llamada, no deja de llamar |
| Tiempo máximo de respuesta | 30 s (`ESPERA`) | No |
| Reintento automático | Cada 3 min, lotes de 20, hasta **20 intentos** por comprobante | No: sigue llamando aunque SUNAT esté caída |
| Cola de envío | **Un hilo** (`envio-sunat`), 1,5 s entre envíos | No |
| `envioDetenido` | Si SUNAT rechaza el **usuario o la clave SOL**, no se envía nada más hasta reiniciar | **Casi**: es un circuito que se abre por credenciales, pero no se vuelve a cerrar solo |

**Hueco.** Si SUNAT no responde, cada comprobante pendiente ocupa la cola hasta 30 s y gasta uno de sus 20
intentos.

**Diseño.** Va dentro de `SunatDirectoClient`, la capa anticorrupción, para que el dominio no sepa que existe:

| Parámetro | Valor | Por qué |
|---|---|---|
| Umbral para **abrir** | **50 % de fallos** (tiempo agotado, error de red o HTTP 5xx) en las **últimas 10 llamadas** | Una tasa sobre una ventana, no "N fallos seguidos": un fallo aislado entre aciertos no abre el circuito, y una caída real sí aunque haya un acierto de suerte en medio |
| Qué **no** cuenta como fallo | Un rechazo con CDR (2xxx), una observación (4xxx), 1033 | SUNAT respondió: el problema es del comprobante, no de la conexión |
| Tiempo **abierto** | **5 minutos** | SUNAT suele volver en minutos; JD tiene 3 días de plazo legal, no hay apuro de segundos |
| **Semiabierto** | 1 llamada de prueba; si responde, se cierra; si no, 5 minutos más | No se golpea a SUNAT con todo el lote al volver |
| **Fallback** | El comprobante queda **PENDIENTE sin gastar un intento**, con el aviso "SUNAT no responde: se enviará solo cuando vuelva" en la campana; la venta ya está guardada y el cliente se lleva su comprobante impreso | Tiene sentido de negocio: Lucy sigue vendiendo, el plazo de 3 días sigue corriendo y la campana avisa si se acerca el vencimiento (ya implementado, `PlazoEnvioDTO`) |

La venta **nunca** espera a SUNAT: el envío ya va en segundo plano, después del `COMMIT` (ADS S4). El Circuit Breaker
no protege la pantalla de Lucy, que ya está protegida, sino los **intentos** y la **cola**.

**Implementado el 11/11/2026** (`facturacion/sunat/integration/CircuitoSunat`), con los valores de esta tabla:
- **Dónde:** el circuito vive dentro de `SunatDirectoClient`. El puerto `OseClient` solo ganó `disponible()` y `avisoNoDisponible()`, con valores por defecto, así que el envío simulado no cambia.
- **Fallback:** con el circuito abierto, `EnvioSunatService` deja el comprobante o la nota en PENDIENTE **sin llamar a SUNAT y sin gastar el intento**. La campana muestra "SUNAT no responde desde las HH:MM…" (campo `sunatSinRespuesta`, aviso de nivel medio).
- **Detalle que la tabla no fijaba:** con menos de 5 llamadas en la ventana no se decide nada, para que un solo fallo al arrancar no abra el circuito.
- **Pruebas:** `CircuitoSunatTest`, 7, con un reloj controlado (se abre con 5 de 5 y no con 4; espera 5 minutos; una sola prueba en semiabierto; vuelve a abrirse si la prueba falla; libera el turno si no se llamó); `EnvioSunatServiceTest`, con el circuito abierto no hay intento ni llamada; y la prueba del aviso en el frontend.

## 3. Evento de dominio: `VentaAnuladaEvent`

| | Hoy (implementado) | Diseño |
|---|---|---|
| Publicador | `VentaService.anular()` | Igual |
| Datos | **`(Long ventaId)`** | `(ventaId, clienteId, moneda, fechaAnulacion, documento)` |
| Consumidores | `VentasFacturacionListener` (emite la nota de crédito), `VentasPostventaListener` (cancela mantenimientos), `DevolucionService` (deja la devolución pendiente) | Igual |

**Por qué evento y no llamada directa.** `ventas` no tiene por qué saber que existen la nota de crédito, los
mantenimientos o las devoluciones. Hoy hay **tres** reacciones distintas a una anulación, en tres módulos. Con
llamadas directas, `ventas` dependería de los tres, y de un cuarto el día que se agregue. Con el evento, cada módulo
se suscribe y `ventas` no cambia (Observer, S10). Además, la cardinalidad está abierta: no se sabe cuántos lo van a
necesitar.

**El consumidor no debe llamar de vuelta.** Hoy lo hace:

```java
// cobranza/devolucion/service/DevolucionService.java
@EventListener
public void alAnularVenta(VentaAnuladaEvent evento) {
    Venta venta = ventaService.obtenerPorIdPublico(evento.ventaId());   // ← llama de vuelta a ventas
    BigDecimal pagado = cobranzaService.pagadoDe(venta);
    …
    devolucion.setCliente(venta.getCliente());  devolucion.setMoneda(venta.getMoneda());
```

Como el evento lleva solo el ID, Cobranza tiene que pedirle a Ventas el cliente, la moneda y la fecha. Con el
evento diseñado, `DevolucionService` tiene todo lo que necesita: cliente, moneda y fecha vienen en el evento, y lo
pagado es dato **propio** de cobranza. **Consumidor previsto que tampoco llama de vuelta:** un resumen semanal para
Lisandro ("anulaciones de la semana, por motivo"), que solo acumula los datos que trae el evento.

## 4. Saga coreografiada: una venta con su comprobante, hasta que SUNAT la acepta

Es el proceso principal de S1, y tiene un paso remoto que puede fallar de dos maneras distintas:

| Paso | Módulo / sistema | Si falla | Compensación |
|---|---|---|---|
| 1. Registrar la venta y su comprobante, con correlativo (una transacción) | `ventas` + `facturacion` (local) | No queda nada: ni venta ni número consumido (ADS S4: 0 huecos) | No aplica |
| 2. Enviar el comprobante a SUNAT | `facturacion` → SUNAT (capa anticorrupción y Circuit Breaker) | SUNAT no responde o el circuito está abierto | **Fallback del Circuit Breaker**: queda PENDIENTE y se reintenta. **No es la compensación de la Saga**: la venta sigue siendo válida y no hay nada que deshacer |
| 3. SUNAT responde con un **CDR de rechazo** (2xxx) | SUNAT → `facturacion` | El comprobante no tiene validez; su número ya está usado | **Compensación real:** se emite un **comprobante de reemplazo** para la misma venta, con número nuevo. El rechazado queda archivado como evidencia y no se corrige ni se reenvía (`FacturacionService.emitirReemplazo`, implementado) |
| 4. Se vence el plazo de 3 días sin aceptación | `facturacion` | Lo enviado fuera de plazo no tiene validez | Aviso en la campana (`fueraDePlazo`); la compensación es otro reemplazo, dentro de plazo |
| 4′. (Variante) La venta se anula después de aceptada | `ventas` → evento → `facturacion` → SUNAT | La nota de crédito puede ser rechazada | Nota de crédito nueva y corregida. **La venta no vuelve a REGISTRADA**: el trigger de BD2 S2 lo impide |

**Fallback frente a compensación.** El fallback del paso 2 reacciona a que **no hubo respuesta**: el estado es
"no sé" y se espera. La compensación del paso 3 reacciona a una **respuesta negativa**: el estado es "SUNAT dijo que
no", y hay que hacer algo nuevo que restablezca lo que el negocio necesita (un comprobante válido). Si se confunden,
se reenvía un rechazado, que SUNAT contesta con 1032, o se "compensa" una simple demora emitiendo comprobantes de
más.

Es **coreografiada**: no hay un orquestador central. Cada paso reacciona al resultado del anterior por evento o
por estado (`ComprobanteAceptadoEvent` → correo al cliente, implementado, con su propia tabla de envíos que funciona
como *outbox*: `ENVIOS_CORREO`).

## 5. Matriz de integración

| Qué cruza la frontera | Dirección | Tipo | Patrón que la protege |
|---|---|---|---|
| Factura, boleta y nota de crédito (UBL 2.1) | SGE-JD → SUNAT | Síncrona, obligatoria, en segundo plano | Capa anticorrupción (`OseClient`) + Circuit Breaker (diseñado) + reintentos (implementados) |
| CDR de rechazo | SUNAT → SGE-JD | Respuesta | Saga: comprobante de reemplazo (implementado) |
| RUC y DNI | SGE-JD → apis.net.pe | Síncrona, opcional | Capa anticorrupción (`ConsultaRucClient`) + **fallback encadenado implementado** (`ConsultaRucClientConRespaldo`): caché → apis.net.pe → Decolecta → el usuario lo escribe a mano |
| Tipo de cambio | SGE-JD → apis.net.pe (dato de SUNAT) | Síncrona | Caché por día (`TipoCambioService`); si no hay, 503 y se pide escribirlo (implementado) |
| Correo del comprobante | SGE-JD → servidor SMTP | Asíncrona | Outbox (`ENVIOS_CORREO`, `INTENTOS_CORREO`), reintentos, estado INCIERTO (implementado) |
| `VentaAnuladaEvent` | `ventas` → `facturacion`, `postventa`, `cobranza` | Asíncrona en el diseño (hoy, síncrona en la misma transacción) | Evento de dominio (Observer) |
| `VentasParaCobranza` | `cobranza` → `ventas` | Síncrona, **interna** | Protected Variations (S8, S10). **Sin capa anticorrupción**: los dos módulos hablan el mismo lenguaje ubicuo (S6) |

## 6. Error o hallazgo

**Hallazgo 1: el consumidor del evento llamaba de vuelta al publicador.** Al diseñar el evento, lo primero fue copiar
el que existe: `VentaAnuladaEvent(Long ventaId)`. Al revisar sus consumidores, `DevolucionService.alAnularVenta`
empieza con `ventaService.obtenerPorIdPublico(evento.ventaId())`. Recibe el aviso y vuelve a preguntarle todo a
ventas. Así, el evento desacopla solo de nombre: cobranza sigue dependiendo de que ventas le responda en ese mismo
momento. El evento rediseñado lleva lo que los consumidores necesitan (cliente, moneda, fecha, documento).

**Hallazgo 2: una caída larga de SUNAT agotaría los intentos.** Comprobado en el código: cada respuesta, también el tiempo agotado (`REINTENTAR`), suma un intento (`EnvioSunat.registrarIntento`: `intentos++`), y el reintento automático solo toma los que tienen menos de 20 (`pendientesParaReintentar(…, MAX_INTENTOS, …)`). Al poner números al "Circuit Breaker" que creía
tener, salió que no lo hay. Ejemplo con **5 comprobantes pendientes** y SUNAT sin responder:

- cada vuelta de reintento tarda unos 2,6 minutos (5 × (30 s de espera + 1,5 s de pausa));
- la siguiente vuelta empieza 3 minutos después de terminar la anterior;
- cada comprobante gasta 1 intento cada ~5,6 minutos;
- los 20 intentos se agotan en **menos de 2 horas**.

Después, esos comprobantes **dejan de reintentarse solos** y hay que reenviarlos a mano, con el plazo de 3 días
corriendo. El fallback diseñado lo evita: con el circuito abierto, no se llama y no se gasta ningún intento.

**Fallback descartado:** "reintentar hasta que responda", sin tope. Mientras SUNAT esté caída, la cola de un solo
hilo seguiría ocupada esperando 30 s por cada comprobante, y los comprobantes nuevos quedarían detrás de los viejos.

## 7. Reflexión

¿Qué pasaría si SUNAT se cayera dos horas y la integración no tuviera Circuit Breaker ni fallback? Relación con
Netflix.

En SGE-JD la pantalla de ventas no se caería, porque el envío ya va en segundo plano. Lucy seguiría vendiendo e
imprimiendo, y eso ya es un fallback de diseño. Pero por detrás:

- la cola de un solo hilo pasaría las dos horas esperando 30 s por cada comprobante;
- los comprobantes nuevos quedarían detrás de los viejos;
- con unos pocos pendientes, los 20 intentos se agotarían en menos de dos horas (hallazgo 2);
- al volver SUNAT, nadie reenviaría esos comprobantes, y el plazo de 3 días seguiría corriendo.

Netflix construyó Hystrix después de ver que un servicio lento, no caído, arrastraba a todos los que lo llamaban,
porque cada llamada ocupaba un hilo esperando. La lección es la misma: lo peligroso no es que el sistema externo
falle, sino seguir llamándolo como si no fallara. El circuito abierto convierte "esperar 30 s por cada comprobante"
en "no llamar y avisar", y conserva los intentos para cuando SUNAT vuelva.
