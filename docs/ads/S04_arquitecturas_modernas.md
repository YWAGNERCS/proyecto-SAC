# ADS · S4 · Arquitecturas modernas: el estilo de SGE-JD con evidencia

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 4 (*Arquitecturas modernas*). En [S1](S01_vision_arquitectonica.md) se eligió
monolito modular. Aquí esa elección se pone a prueba con evidencia real: dos instancias del backend corriendo
a la vez, la prueba de módulos y el alcance real de una transacción.

## 1. Trade-offs de los siete estilos, aplicados a SGE-JD

| Estilo | ¿Aplica hoy? | Qué se ganaría | Qué se pagaría |
|---|---|---|---|
| **Monolito modular** | **Sí** | **ACID por defecto:** registrar una venta escribe `VENTAS`, `DETALLE_VENTA`, el stock de `PRODUCTOS`, el correlativo de `SERIES_COMPROBANTE`, el `COMPROBANTE` y los `MANTENIMIENTOS` en **una sola transacción** de Oracle, aunque toque 4 módulos (sección 2). **ADP verificado:** `ModularityTests` falla si aparece un ciclo entre módulos | No se puede escalar o desplegar un módulo por separado: si Facturación necesita más recursos, se escala todo |
| Arquitectura en capas | Sí, dentro de cada módulo | Controller → Service → Repository → Entity, con DTO en el borde | No pone límites entre módulos; eso lo da Spring Modulith |
| Hexagonal | **Solo en las integraciones** | Ya se usa donde la infraestructura cambia: `OseClient` tiene dos adaptadores (`NubefactClientSimulado` y `SunatDirectoClient`), y `NotificadorCorreo` también | Llevarla a todo el dominio agregaría puertos y mapeos que hoy no protegen ninguna regla (sección 4) |
| Clean Architecture | Parcial: los principios | El dominio no depende de Spring MVC; las entidades calculan su propio total | La estructura formal (casos de uso, presentadores) duplicaría lo que ya separan las capas |
| Microservicios | **No** | Escalar y desplegar por separado | **Teorema CAP:** la venta y su comprobante quedarían en dos servicios con dos bases. Si la red entre ellos falla (partición), habría que elegir entre **rechazar la venta** (consistencia) o **guardarla sin comprobante y completarla después** (disponibilidad, consistencia eventual con una Saga). Hoy esa elección no existe: o se guarda todo o no se guarda nada |
| Serverless | No | Pagar por invocación | Arranque en frío para 2 usuarios; tareas programadas (reintentos a SUNAT, alertas de las 8:00) que necesitan un proceso vivo; el certificado digital tendría que vivir en la nube |
| Microfrontend | No | Desplegar cada pantalla por separado | Un solo equipo de 3 con una sola SPA; coordinaría versiones sin ninguna necesidad |

**CAP ya aparece hoy, en un solo lugar: SUNAT.** El envío a SUNAT es el único paso que cruza una red ajena, y
por eso no está dentro de la transacción. Va en una cola aparte (`EnvioSunatService`) con reintentos, estado
PENDIENTE y estado INCIERTO. Es exactamente la consistencia eventual que habría que construir entre
microservicios propios. Esa experiencia es el mejor argumento para no multiplicarla sin necesidad.

## 2. ACID y ADP con evidencia

**ACID.** `VentaService` está anotada con `@Transactional` a nivel de clase (línea 43). Los oyentes de los otros
módulos son **síncronos** (`@EventListener`, no `@TransactionalEventListener` ni `@Async`):

| Paso de `registrarVentaDirecta` | Módulo | Tabla | ¿Misma transacción? |
|---|---|---|---|
| Cabecera y líneas | ventas | `VENTAS`, `DETALLE_VENTA` | Sí |
| Descuento de stock (`ProductoService.descontarStock`) | catalogo | `PRODUCTOS` | Sí |
| Comprobante y correlativo (`VentasFacturacionListener.alRegistrarVenta`) | facturacion | `COMPROBANTES`, `SERIES_COMPROBANTE` | Sí |
| Rutina de mantenimiento (`VentasPostventaListener.alRegistrarVenta`) | postventa | `MANTENIMIENTOS` | Sí |
| Envío a SUNAT | facturacion | (red externa) | **No:** se encola después, en segundo plano |

Si falla cualquiera de los cuatro primeros pasos, por ejemplo un stock insuficiente, no queda ni la venta ni el
número de comprobante consumido. Lo comprobó la prueba de la sección 3: con 2 621 fallos de inserción, las series
quedaron **sin un solo hueco**.

**ADP (Acyclic Dependencies Principle).**

```
> mvn -q test -Dtest=ModularityTests
Test set: com.bomerp.ModularityTests
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.119 s
```

`ApplicationModules.of(BomerpApplication.class).verify()` falla la compilación si un módulo usa clases internas
de otro, por ejemplo un repositorio, o si se forma un ciclo. Ventas llama a Catálogo, y Catálogo no llama a
Ventas. Ventas avisa a Facturación y Postventa por eventos, sin conocerlos.

## 3. Escalabilidad horizontal: dos instancias de verdad

**Prueba** ([evidencia/S04_dos_instancias.ps1](evidencia/S04_dos_instancias.ps1)). Se levantaron dos instancias
del mismo jar en los puertos 8081 y 8082, contra la **misma** Oracle: la base de práctica de BD2, con 30 241
ventas. Para que nada saliera de la PC se usaron SUNAT en modo simulado, el correo hacia un puerto donde nadie
escucha y `ddl-auto=validate`.

```
Started BomerpApplication in 19.797 seconds   (8081)
Started BomerpApplication in 19.794 seconds   (8082)
GET :8081/actuator/health -> 200  (status UP; componente db: Oracle, UP)
GET :8082/actuator/health -> 200  (status UP; componente db: Oracle, UP)
GET :8081/api/v1/admin/ventas sin token -> 401
GET :8082/api/v1/admin/ventas sin token -> 401
```

**La capa web escala.** Las dos responden de forma independiente, sin configuración compartida, y cada una
valida el token por su cuenta.

**Las tareas programadas, no.** A los 20 s, las dos ejecutaron la misma tarea,
`FacturacionService.emitirVentasPorEmitir()` (`@Scheduled`), **con 44 ms de diferencia**, sobre la misma lista:

```
19:15:11.621  8082  Emitiendo el comprobante de 29092 venta(s) que estaban por emitir
19:15:11.665  8081  Emitiendo el comprobante de 29092 venta(s) que estaban por emitir

Instancia 8081: emitidos 141   | rechazados por UX_COMPROBANTE_VIGENTE_VENTA: 2480
Instancia 8082: emitidos 2481  | rechazados por UX_COMPROBANTE_VIGENTE_VENTA:  141
```

Compitieron venta por venta. De 5 243 intentos, **2 621 (50 %) fueron trabajo duplicado** que Oracle rechazó
con ORA-00001. Después se verificó la base:

```
SERIE  COMPROBANTES  DESDE  HASTA  HUECOS  NUMEROS_REPETIDOS
B001           1420      1   1420       0                  0
F001           1439      1   1439       0                  0
VENTAS_CON_DOS_VIGENTES: 0
```

**La integridad se sostuvo, pero no gracias al diseño del backend.** La salvaron dos piezas de la base de datos:
el índice único `UX_COMPROBANTE_VIGENTE_VENTA` y el correlativo dentro de la misma transacción, que se deshace
si la inserción falla.

| Tarea o estado en memoria | Qué pasa con 2 instancias | Protección actual |
|---|---|---|
| `emitirVentasPorEmitir` (cada 3 min) | **Medido:** las dos emiten las mismas ventas, y 50 % del trabajo se desperdicia | Índice único en Oracle: sin duplicados ni huecos |
| `EnvioSunatService.reintentarPendientes` (cada 3 min) | Las dos pueden enviar a SUNAT el mismo comprobante PENDIENTE: `enviarComprobante` lo lee con `findById`, **sin bloqueo** | Ninguna en el backend; SUNAT respondería 1033 "ya registrado" al segundo envío |
| `envioDetenido` (credenciales SOL rechazadas) | **Estado en memoria:** si la instancia A lo detecta, la B sigue enviando con la clave mala y puede bloquear la cuenta SOL de JD | Ninguna |
| Cola `envio-sunat` (un hilo, 1,5 s entre envíos) | Dos colas: el doble de ritmo hacia SUNAT | Ninguna |
| Alertas de mantenimiento y de stock (cron 08:00) | Dos correos a la oficina cada mañana | Ninguna (la de mantenimiento marca `alertaEnviada`, pero las dos leen antes de marcar) |
| Correo de comprobantes | Seguro | Toma del envío con `UPDATE` condicional (`tomar`) y bloqueo de la fila |
| Cache del tipo de cambio | Inofensivo: cada instancia guarda el mismo dato público | — |
| Archivo de log `spring.log` | **Medido:** las dos escriben el mismo archivo en `%TEMP%`, y logback falló al rotarlo (`Failed to rename file … spring.log`) | Ninguna |

## 4. ¿Es stateless?

**Las peticiones HTTP sí, por diseño:**

```java
// seguridad/SecurityConfig.java:99
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

No hay `HttpSession`. Cada petición trae su JWT, firmado por Keycloak, y cada instancia lo valida con la clave
pública, sin preguntarle a nadie. Quién vende o anula sale del token (`jwt.getSubject()`,
`preferred_username`), nunca de memoria. En la prueba, las dos instancias respondieron 401 por su cuenta. Ningún
servicio guarda datos de un usuario en un campo entre peticiones. Los campos son dependencias `final`.

**El proceso no es stateless del todo.** `EnvioSunatService` guarda estado de negocio en memoria
(`private volatile String envioDetenido`) y tiene su propia cola. Para un solo servidor es correcto y simple.
Para dos, ese estado tendría que estar en Oracle.

## 5. ¿El dominio justifica hexagonal o Clean Architecture?

| Módulo | Complejidad real | ¿Aislar el dominio? |
|---|---|---|
| `catalogo` | CRUD con precio por margen y stock con bloqueo | No: la indirección no protegería ninguna regla |
| `ventas` | Agregado real: `Venta`–`DetalleVenta` con total, IGV, topes, stock, anulación definitiva y fecha pasada con permiso | Todavía no. Sus reglas dependen de fachadas de 5 módulos; primero hay que separar sus interfaces (hallazgo de [S3](S03_solid_modulo_ventas.md)) |
| `facturacion` | El más complejo: UBL 2.1, firma, plazo de 3 días, reemplazos, notas de crédito, ambiente inmutable, y una infraestructura que **cambia** (simulado, beta, producción) | **Sí, y ya está hecho donde importa.** `OseClient` es un puerto con dos adaptadores; la regla "solo direcciones oficiales" está en su propia clase (`AmbienteSunat`), con su propia prueba (`AmbienteSunatTest`). Llevarlo más lejos (UBL independiente de JAXB) no tiene hoy un segundo uso que lo justifique |

Valdría la pena aislar más el dominio de ventas si JD vendiera por un segundo canal, como una tienda web o
WhatsApp con pedidos. Las mismas reglas tendrían que servir a dos entradas distintas.

## 6. Conclusión: confirmación con un hallazgo

**Se confirma el monolito modular** para JD: dos usuarios, una PC o un servidor, ACID entre 4 módulos sin
esfuerzo, y ADP verificado en cada compilación. Microservicios cambiarían una transacción por una Saga sin
ningún beneficio para este volumen.

**Hallazgo:** la escalabilidad horizontal estaba "confirmada" en papel (sin sesión, JWT), pero la prueba real
mostró que **solo la capa web escala**. Las tareas programadas y el estado de `EnvioSunatService` suponen una
sola instancia, y con dos se duplica el trabajo (50 % medido). También se podría enviar dos veces a SUNAT y
seguir enviando con credenciales rechazadas.

**Decisión (ADR-S4-01):** SGE-JD se despliega con **una sola instancia del backend**, que ya es lo que hacen
`compose-prod.yml` y la PC de JD. Esa restricción queda documentada. Si algún día hace falta una segunda, las
tareas tienen que coordinarse antes:

1. `SELECT … FOR UPDATE SKIP LOCKED` al tomar comprobantes pendientes, para que cada instancia tome los suyos.
2. `envioDetenido` guardado en una tabla.
3. Un candado distribuido (ShedLock, sobre Oracle) para las tareas de las 8:00.
4. Un archivo de log por instancia.

## 7. Integración con LP2 y BD2

| Criterio | Evidencia en LP2 | Relación con BD2 |
|---|---|---|
| Monolito modular, ADP | `ModularityTests` 2/2 | Un esquema propietario, `JDREFRIGERACION` |
| ACID entre módulos | `@Transactional` en `VentaService` y `@EventListener` síncronos | El correlativo se deshace con la transacción: 0 huecos |
| Escalabilidad horizontal | Dos instancias en 8081 y 8082 | El índice único `UX_COMPROBANTE_VIGENTE_VENTA` evitó 2 621 duplicados. La base es la última línea de defensa (igual que las CHECK de BD2 S1) |
| Stateless | `SessionCreationPolicy.STATELESS`, JWT | — |
| Tareas en segundo plano | `@Scheduled` de facturación, postventa e inventario | BD2 S4: el backend factura solo las ventas insertadas por PL/SQL |

## 8. Reflexión

¿Por qué una decisión arquitectónica tomada al inicio necesita revisarse con evidencia?

Porque al inicio se decide con lo que se sabe, y el sistema sigue creciendo. En S1, "stateless con JWT" parecía
suficiente para decir que el backend escalaba. Pero después llegaron las tareas programadas de SUNAT y del
correo, la cola de envíos y el aviso de credenciales rechazadas, y ninguna de esas piezas existía cuando se tomó
la decisión. Solo al levantar dos instancias de verdad se vio que el 50 % del trabajo se duplicaba y que la
integridad la estaba sosteniendo un índice de Oracle, no el diseño. La decisión de usar monolito modular sigue
siendo correcta, pero ahora se sabe con precisión qué la sostiene y bajo qué condición dejaría de valer.
