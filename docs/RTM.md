# Matriz de trazabilidad de requisitos (RTM) · SGE-JD

**JD Refrigeración S.A.C. · Equipo CherryTree · actualizada al 10/11/2026** (ADS S14: RF-17 y RF-20 trazados; RF-11 y RF-17 corregidos)

Cada requisito se sigue de punta a punta: **requerimiento → arquitectura → base de datos → backend → pantalla → prueba**.
Cuando un eslabón todavía no existe, se registra como **pendiente** en vez de inventarlo.

- Requisitos: [README.md](README.md), sección 1 (RF-01 a RF-35, RNF-01 a RNF-09).
- Rutas de las pantallas: `bomerp-frontend/src/app/app.routes.ts`. Todas requieren sesión, salvo `/login`.
- API: todas las rutas empiezan con `/api/v1/admin` (con JWT), salvo `/api/v1/publico/catalogo`.
- Pruebas automáticas: `bomerp-backend/src/test/java/com/bomerp/`. Se ejecutan con `mvn test`; las marcadas con
  **(Oracle)** necesitan la base encendida.
- Pruebas manuales con tokens reales de Keycloak: `bomerp-backend/pruebas-seguridad/*.ps1`.

**Estado:** ✅ cumplido y con prueba · 🔶 cumplido, pero la prueba es solo manual · ⚠️ cumplido a medias (falta algo
externo) · ⏳ pendiente.

## 1. Requisitos funcionales

| Código | Requerimiento | Arquitectura (módulo · decisión) | Base de datos | Backend (API) | Pantalla | Prueba | Estado |
|---|---|---|---|---|---|---|---|
| RF-01 | Inicio de sesión seguro | `seguridad` · Keycloak, Authorization Code + PKCE (ADR-005) | Base propia de Keycloak (contraseñas fuera de la app) | `SecurityConfig`: todo `/admin/**` exige JWT RS256 | `/login`, `/auth/callback` | `01_login_token.ps1`, `06_s11_pkce.ps1`; `FlujoCompletoEndToEndTest` (sin token → 401) | ✅ |
| RF-02 | Roles y permisos (ADMIN, ASISTENTE) | `seguridad` · roles del realm `bomerp` | Roles en Keycloak | `SecurityConfig` (reglas por ruta), `jwtAuthenticationConverter` | Menú según rol, `/sin-permiso` | `05_matriz.ps1` (14 casos); `FlujoCompletoEndToEndTest` (ASISTENTE anula → 403) | ✅ |
| RF-03 | Catálogo con moneda dual y precio por margen | `catalogo` | `PRODUCTOS`, `CATEGORIAS` | `/productos`, `/categorias` | `/catalogo/productos`, `/catalogo/categorias` | `ProductoCrudTest` (Oracle), `02_proteccion_catalogo.ps1`, `03_modificacion_admin.ps1` | ✅ |
| RF-04 | Autocompletar cliente/proveedor por RUC o DNI | `clientes`, `proveedores` · API externa apis.net.pe | `CLIENTES`, `PROVEEDORES` | `GET /clientes/autocompletar/ruc/{ruc}` y `/dni/{dni}` | `/clientes/nuevo`, `/proveedores/nuevo`, diálogo "Nuevo cliente" de venta y proforma | Manual; **prueba automática pendiente** | 🔶 |
| RF-05 | Proformas con IGV y subtotales | `proformas` | `PROFORMAS`, `ITEMS_PROFORMA` | `POST /proformas`, `GET /proformas/pagina` | `/proformas/nueva`, `/proformas` | `FlujoCompletoEndToEndTest` (crea, acepta, convierte) | ✅ |
| RF-06 | PDF corporativo de la proforma | `proformas.pdf` · `GeneradorPdfService` | — (se genera al enviar) | `POST /proformas/{id}/enviar` | `/proformas` (Enviar) | Manual; **prueba automática pendiente** | 🔶 |
| RF-07b | Entrega de la factura o boleta por correo (PDF + XML firmado), con seguimiento, reintentos y reenvío | `facturacion.correo` · `EntregaComprobanteService` ← evento `ComprobanteAceptadoEvent`; `notificaciones` (SMTP) | `ENVIOS_CORREO`, `INTENTOS_CORREO`, `COMPROBANTES.AMBIENTE_EMISION` (fijo, disparador) | `GET /facturacion/{id}/correo`, `POST /facturacion/{id}/enviar`, `GET /facturacion/{id}/pdf`; conciliación periódica | `/ventas` ("Entrega del comprobante") | `ReglasCorreoComprobanteTest` (14), `NotificadorCorreoRealTest` (5), `EntregaComprobanteIntegracionTest` (17), `entrega-correo.spec.ts` (5) | ✅ |
| RF-07 | Envío de la proforma por correo | `notificaciones` · SMTP | — | `POST /proformas/{id}/enviar` | `/proformas` (Enviar) | Manual con Gmail (26/10); **pendiente el correo de la empresa** | ⚠️ |
| RF-08 | Convertir proforma aceptada en venta | `ventas` ← `proformas` (fachada `ProformaService`) | `VENTAS`, `PROFORMAS.VENTA_ID` | `POST /ventas/desde-proforma/{id}` | `/proformas` (diálogo Convertir) | `FlujoCompletoEndToEndTest` (CONVERTIDA, precios y TC respetados) | ✅ |
| RF-09 | Control de créditos y deudas atrasadas | `ventas`, `cobranza` | `VENTAS` (tipo de pago, fecha límite, estado de pago) | `GET /ventas/pagos-atrasados`, `GET /cobranza/por-cobrar` | `/ventas` (Pagos atrasados), `/cobranza` | `CobranzaServiceTest`; `FlujoCompletoEndToEndTest` | ✅ |
| RF-10 | Facturación electrónica UBL 2.1 | `facturacion` · envío directo a SUNAT (SEE Del Contribuyente), evento `VentaRegistradaEvent` | `COMPROBANTES`, `ENVIOS_SUNAT`, `NOTAS_CREDITO`, `SERIES_COMPROBANTE` | `/facturacion/estados`, `/{id}/reenviar`, `/{id}/reemplazar`, `/{id}/envios`, `/plazo-envio`, `/{id}/xml`, `/{id}/cdr` | `/ventas` (estado SUNAT, XML, CDR, Reenviar, comprobante de reemplazo, historial de envíos, plazo de 3 días) | `EnvioSunatServiceTest`, `ClasificacionCodigosSunatTest`, `ComprobanteReemplazoIntegracionTest`, `SunatBetaTest` (beta real, `SUNAT_BETA=1`), `FlujoCompletoEndToEndTest` (comprobante PENDIENTE) | ⚠️ falta usuario SOL y certificado de JD |
| RF-11 | Descuento de stock atómico al vender | `catalogo` (fachada `ProductoService.descontarStock`) ← `ventas` | `PRODUCTOS.STOCK_ACTUAL` (bloqueo de fila) | `POST /ventas/directa`, `/ventas/desde-proforma/{id}` (409 sin stock) | `/ventas/nueva` | `StockProductoTest`; `FlujoCompletoEndToEndTest` (10 → 7 → 10 al anular) | ✅ |
| RF-12 | Incremento de stock al comprar; subtotal e IGV de la compra y último costo del producto (auditoría M1 y M2) | `compras` → `catalogo` | `COMPRAS` (subtotal, igv), `DETALLE_COMPRA`, `PRODUCTOS.ULTIMO_COSTO_COMPRA` | `POST /compras` | `/compras/nueva` | `CompraServiceTest`, `PrecioDeLineaTest`, `PrecioEditadoIntegracionTest` | ✅ |
| RF-13 | Alerta diaria de stock mínimo | `inventario` · `@Scheduled` 8:00 | `PRODUCTOS.STOCK_MINIMO` | `InventarioScheduledService` | Campana de avisos | Manual; **prueba automática pendiente** | ⚠️ falta el correo de la empresa |
| RF-14 | Rutina de mantenimiento al vender equipos | `ventas` → `postventa` por evento (`VentaRegistradaEvent` + `RutinaMantenimiento`, ADR-002) | `MANTENIMIENTOS` | `rutinaMantenimiento` en `POST /ventas/directa` y `/ventas/desde-proforma/{id}` | `/ventas/nueva` y `/proformas` (botón "Implementar rutinas de mantenimiento") | `RutinaMantenimientoTest`, `RutinaMantenimientoIntegracionTest` (Oracle), `FlujoCompletoEndToEndTest` | ✅ |
| RF-15 | Aviso de mantenimientos próximos | `postventa` · `@Scheduled` 8:00 | `MANTENIMIENTOS.ALERTA_ENVIADA` | `MantenimientoScheduledService`, `POST /mantenimientos/simular-alerta` | Campana, `/postventa` | Manual (`simular-alerta`); **prueba automática pendiente** | ⚠️ falta el correo de la empresa |
| RF-16 | Reportes por moneda | `reportes`, `ventas` | `VENTAS`, `DETALLE_VENTA`, `COBROS` | `GET /ventas/pagina` (resumen), `/reportes/ventas-resumen`, `/reportes/cobranza` | `/ventas`, `/cobranza`, `/` (tablero) | `FlujoCompletoEndToEndTest` (total del servidor = suma de todas las páginas) | ✅ |
| RF-17 | Anular venta | `ventas` → `catalogo` por **fachada** (`ProductoService.sumarStock`, misma transacción); → `facturacion`, `postventa`, `cobranza` por `VentaAnuladaEvent` *(corregido 10/11: el stock nunca viajó por el evento)* | `VENTAS.ESTADO`, `NOTAS_CREDITO`, `DEVOLUCIONES` | `PATCH /ventas/{id}/anular` (409 si ya estaba anulada) | `/ventas` (Anular) | `FlujoCompletoEndToEndTest` (stock, visitas canceladas, devolución, 409) | ✅ |
| RF-18 | Venta en soles o dólares con tipo de cambio | `config.tipocambio` | `VENTAS.MONEDA`, `VENTAS.TIPO_CAMBIO` | `GET /tipo-cambio/hoy` (Cache-Control 10 min) | `/ventas/nueva`, `/proformas/nueva` | `VentaCostoTest`, `DeduccionTributariaServiceTest` (dólares), `OrdenTest` (total en soles) | ✅ |
| RF-19 | Descuento por línea con tope por rol | `seguridad.descuento` · `TopeDescuentoService` | `DETALLE_VENTA.DESCUENTO_PORCENTAJE`, `ITEMS_PROFORMA` | `GET /descuentos/tope`; `GET`/`PUT /descuentos/topes` (cambiar: solo ADMIN); validado al registrar | `/ventas/nueva`, `/proformas/nueva`, `/configuracion` | `VentaCostoTest` (subtotal con descuento), `TopeDescuentoServiceTest` (tope por rol y topes del administrador) | ✅ |
| RF-20 | Cobranza con abonos | `cobranza` | `COBROS`, `DETALLE_COBRO` | `POST /cobranza/cobros`, `GET /cobranza/por-cobrar` | `/cobranza` | `CobranzaServiceTest` (9); `FlujoCompletoEndToEndTest` (abono parcial y total) | ✅ |
| RF-21 | Agenda de postventa | `postventa` | `MANTENIMIENTOS` | `GET /mantenimientos`, `PATCH /{id}/realizado`, `/{id}/reprogramar` | `/postventa` | `FlujoCompletoEndToEndTest` (visitas de la venta); realizar y reprogramar: manual | 🔶 |
| RF-22 | Devolución al anular una venta pagada | `cobranza.devolucion` ← `VentaAnuladaEvent` | `DEVOLUCIONES` | `GET /cobranza/devoluciones`, `POST /{id}/registrar` | `/cobranza` (Devoluciones) | `CobranzaServiceTest`; `FlujoCompletoEndToEndTest` (devolución por lo pagado) | ✅ |
| RF-23 | Anular cobros y evitar pagos duplicados | `cobranza` | `COBROS` (estado, motivo, usuario) | `POST /cobranza/cobros/{id}/anular` | `/cobranza` | `CobranzaServiceTest` | ✅ |
| RF-24 | Detracción (SPOT) y retención del IGV | `ventas.tributos` · `DeduccionTributariaService` | `VENTAS` (tipo, % y monto de deducción) | Se aplica al registrar la venta | `/ventas/nueva`, factura PDF | `DeduccionTributariaServiceTest` (5) | ✅ (regla por confirmar con el contador) |
| RF-25 | Órdenes de trabajo | `servicios` | `ORDENES_TRABAJO`, `MATERIALES_ORDEN` | `/ordenes` | `/servicios/ordenes` | `AgendaHorarioTest` (cruces); cierre y facturación: manual | 🔶 |
| RF-26 | Sedes y equipos instalados | `clientes.sede`, `postventa.equipo` | `SEDES_CLIENTE`, `EQUIPOS_INSTALADOS` | `/clientes/{id}/sedes`, `/equipos` | `/servicios/equipos` | Manual; **prueba automática pendiente** | 🔶 |
| RF-27 | Plan por equipo y contratos | `postventa` | `CONTRATOS_MANTENIMIENTO`, `MANTENIMIENTOS` | `/contratos`, `/equipos` | `/servicios/contratos`, `/servicios/equipos` | Manual; **prueba automática pendiente** | 🔶 |
| RF-28 | Permisos delegables | `seguridad.permisos` · roles `PERM_*` en Keycloak | Roles en Keycloak | `/permisos` | `/configuracion/permisos` | `PermisosServiceTest` (6) | ✅ |
| RF-29 | Historial de cambios (auditoría) | `seguridad.auditoria` · filtro en la cadena de seguridad | `HISTORIAL_CAMBIOS` | `GET /auditoria`, `/auditoria/sesiones` | `/configuracion/historial` | `AuditoriaServiceTest` (6); `FlujoCompletoEndToEndTest` (acción EXITO y DENEGADO con el usuario del token) | ✅ |
| RF-30 | Cuentas por pagar a proveedores | `compras` | `COMPRAS`, `PAGOS_PROVEEDOR` | `/cuentas-por-pagar` | `/compras/por-pagar` | `CuentasPorPagarServiceTest` (8) | ✅ |
| RF-31 | Ajustes de inventario | `inventario.ajuste` | `AJUSTES_INVENTARIO` | `/inventario/ajustes` | `/inventario/ajustes` | `AjusteInventarioServiceTest` (7) | ✅ |
| RF-32 | Rentabilidad | `reportes.rentabilidad` | `DETALLE_VENTA.COSTO_UNITARIO` | `/reportes/rentabilidad` | `/reportes/rentabilidad` | `RentabilidadServiceTest`, `VentaCostoTest` | ✅ |
| RF-33 | Agenda de técnicos | `servicios` | `ORDENES_TRABAJO` (hora, duración) | `/ordenes` (409 si se cruzan) | `/servicios/agenda` | `AgendaHorarioTest` (6) | ✅ |
| RF-34 | Ordenar listados por columna | `config.paginacion.Orden` (servidor) y `core/layout/orden.ts` (SPA) | — (ordena antes de paginar) | Parámetro `orden=campo,asc\|desc` en `/ventas/pagina`, `/proformas/pagina`, `/compras/pagina` | Encabezados de las 13 listas | `OrdenTest` (4); verificación en el navegador (07/11) | ✅ |
| RF-35 | Registrar un cliente sin salir de la venta o proforma | SPA · `ClienteFormComponent` en modo diálogo | `CLIENTES` | `POST /clientes` | `/ventas/nueva`, `/proformas/nueva` (botón "Nuevo cliente") | Verificación en el navegador; **prueba automática pendiente** | 🔶 |

## 2. Requisitos no funcionales

| Código | Requerimiento | Arquitectura (decisión) | Base de datos | Backend | Pantalla | Prueba o evidencia | Estado |
|---|---|---|---|---|---|---|---|
| RNF-01 | Monolito modular en capas | ADR-001, ADR-002 (Spring Modulith; los módulos se hablan por fachadas y eventos) | Un esquema, `JDREFRIGERACION` | 15 módulos, uno por paquete | — | `ModularityTests` (límites y ciclos) | ✅ |
| RNF-02 | Respuesta por debajo de 1000 ms | Paginación de alto volumen y caché del tipo de cambio | Índices `IX_HISTORIAL_*`, `IX_AJUSTE_PRODUCTO`, `IX_PAGO_PROV_COMPRA` | `Pagina.java` (20 por página, máximo 100); `Cache-Control` en `/tipo-cambio` | Paginador en Ventas, Proformas y Compras | Medición S13 (`scripts/prod/medir_endpoints.py`, [S13](S13_optimizacion_produccion.md)) | ✅ |
| RNF-03 | API REST JSON versionada | ADR-001 | — | `/api/v1/...` | SPA separada (Angular) | Swagger en el perfil dev | ✅ |
| RNF-04 | Credenciales fuera del código | Variables de entorno, perfiles dev y prod | — | `application-prod.properties` sin valores por defecto | — | `.gitignore` (`.env*`, `certificados/`); `generar_env_prod.py` | ✅ |
| RNF-05 | Seguridad | JWT de Keycloak, roles y permisos | `HISTORIAL_CAMBIOS` | `SecurityConfig`, `AuditoriaFilter` | Guard e interceptor | `05_matriz.ps1`; `FlujoCompletoEndToEndTest` (401, 403, auditoría) | ✅ |
| RNF-06 | SPA responsiva | Diseño por breakpoints (900 y 640 px) | — | — | Todas | Revisión manual en el navegador (escritorio y celular); **prueba automática pendiente** | 🔶 |
| RNF-07 | Tema claro, oscuro o del sistema | Variables CSS por tema | — | — | Configuración, botón sol/luna | Revisión manual | 🔶 |
| RNF-08 | Respaldo automático diario | Data Pump con tarea de Windows | Volcado `.dmp` | `scripts/respaldar_sgejd.ps1`, `programar_respaldo.ps1` | — | Scripts de respaldo y restauración (`respaldar_sgejd.ps1`, `restaurar_sgejd.ps1`); restauración en otra PC: **pendiente de probar** | ⚠️ falta la carpeta fuera de la PC |
| RNF-09 | Diagnóstico en producción | Perfiles, Actuator y logback (S13) | — | `/actuator/health`, `/actuator/info`; logs con nivel por perfil | — | Evidencias S13 (logs y health en producción local) | ✅ |

## 3. Requisitos trazados de punta a punta

### RF-11 · Descuento de stock atómico al vender

| Eslabón | Evidencia |
|---|---|
| Requerimiento | Al vender, el stock de los materiales baja en la misma operación. Si no alcanza, no se guarda nada (409). Los equipos no controlan stock. |
| Arquitectura | `ventas` no toca el repositorio de `catalogo`: usa la fachada `ProductoService.descontarStock` (ADR-002, verificado por `ModularityTests`). |
| Base de datos | `PRODUCTOS.STOCK_ACTUAL`. Las filas se bloquean en orden de id (`SELECT ... FOR UPDATE`) para que dos ventas simultáneas no usen el mismo stock. |
| Backend | `VentaService.registrarVentaDirecta` y `convertirProformaEnVenta` (`@Transactional`); al anular, `VentaService.anular` lo devuelve con `ProductoService.sumarStock` en la misma transacción, antes de publicar `VentaAnuladaEvent` *(corregido 10/11: antes decía que lo devolvía el evento)*. |
| Prueba | `StockProductoTest` (suma de líneas repetidas, todo o nada, equipos sin stock). `FlujoCompletoEndToEndTest` contra Oracle: 10 → 7 al vender y vuelve a 10 al anular. |

### RF-14 · Rutina de mantenimiento al vender equipos

| Eslabón | Evidencia |
|---|---|
| Requerimiento | Al vender un equipo de climatización o refrigeración, el vendedor puede elegir la rutina (cada 1 a 12 meses, 1 a 24 visitas, primera fecha). Sin rutina no se programa nada. |
| Arquitectura | `ventas` publica `VentaRegistradaEvent(ventaId, rutina)` y `postventa` lo escucha. Ventas no conoce a Postventa (ADR-002). |
| Base de datos | `MANTENIMIENTOS` (venta, cliente, fecha programada, estado). |
| Backend | `VentaService.validarRutina` (400/409 si no corresponde), `VentasPostventaListener`, `MantenimientoService.programarParaVenta`. |
| Prueba | `RutinaMantenimientoTest` (5 reglas), `RutinaMantenimientoIntegracionTest` (Oracle: con rutina 6 visitas, sin rutina ninguna), `FlujoCompletoEndToEndTest` (4 visitas trimestrales, canceladas al anular). |

### RF-17 · Anular venta (ADS S14)

| Eslabón | Evidencia |
|---|---|
| Requerimiento | Una venta se anula con motivo (5 a 300 caracteres) y queda anulada para siempre: devuelve el stock, emite la nota de crédito si el comprobante fue aceptado, deja pendiente la devolución de lo cobrado y cancela las visitas. Una segunda anulación responde 409. |
| Arquitectura | Stock: fachada `ProductoService.sumarStock`, **síncrona y en la misma transacción** (todo o nada). Lo demás: `VentaAnuladaEvent` con **tres** oyentes, `VentasFacturacionListener`, `VentasPostventaListener` y `DevolucionService` (ADS S13, H1). Datos del evento: **ADR-008**. |
| Base de datos | `VENTAS.ESTADO`, `MOTIVO_ANULACION`, `ANULADO_POR`, `FECHA_ANULACION`; `NOTAS_CREDITO`, `DEVOLUCIONES`, `MANTENIMIENTOS`. Fila bloqueada con `FOR UPDATE WAIT 10`. **Pendiente:** `TRG_VENTA_ESTADO_BU` y `VENTA_AUDITORIA` (BD2 S2) solo existen en la base de práctica. |
| Backend | `VentaService.anular` (líneas 497–528): `bloquear()`, 409 si ya está anulada, `sumarStock`, publica el evento. Bloqueo de más de 10 s: `GlobalExceptionHandler` (líneas 90–96), 409. |
| Prueba | `FlujoCompletoEndToEndTest` contra Oracle (stock 10 → 7 → 10, visitas canceladas, devolución, 409 en la segunda). BD2 S14: bloqueo real cobranza contra anulación; con `WAIT 10` la anulación se rinde a los 10,03 s con ORA-00054. **Pendiente:** con el trigger de BD2 S2, dos pruebas fallan porque su `UPDATE` no trae motivo ni usuario (BD2 S8). |

### RF-20 · Cobranza con abonos (ADS S14)

| Eslabón | Evidencia |
|---|---|
| Requerimiento | Un cobro reparte un pago entre una o varias ventas a crédito del mismo cliente y moneda; ningún abono supera el saldo; la venta que llega a saldo 0 pasa a PAGADO en la misma transacción; el mismo voucher no entra dos veces. |
| Arquitectura | `cobranza` → `ventas` por fachada (`VentaService`: `obtenerParaCobro`, `obtenerPorIdPublico`, `marcarComoPagada`). Diseño de S8: interfaz `VentasParaCobranza` y record `VentaParaCobro`. Quién crea el abono: **ADR-007**. |
| Base de datos | `COBROS`, `DETALLE_COBRO`, `CK_COBRO_ANULADO`. Práctica (BD2 S5): `IX_COBROS_REFERENCIA_NORM`. **Pendiente:** índice único `UX_COBRO_VOUCHER_VIGENTE` contra la carrera del voucher (ADS S9). |
| Backend | `CobranzaService.registrar` (líneas 145–185): bloquea las ventas en orden, valida cada abono contra el saldo (`Venta.calcularTotal()` menos lo cobrado), `cobro.agregarDetalle(new DetalleCobro(venta, monto))`, `setMontoTotal`, `marcarComoPagada`. |
| Prueba | `CobranzaServiceTest` (9 pruebas); `FlujoCompletoEndToEndTest` (abono parcial y total). **Pendiente:** la prueba de `Cobro.abonar()` cuando se implemente el ADR-007, y la del voucher simultáneo. |

## 4. Pendientes registrados

- **Pruebas automáticas que faltan:** RF-04, RF-06, RF-13, RF-15, RF-26, RF-27, RF-35 y la parte responsiva y de
  temas (RNF-06, RNF-07). Hoy se verifican a mano.
- **Dependen de JD:** el usuario SOL y el certificado (RF-10), el correo de la empresa (RF-07, RF-13, RF-15) y la
  carpeta de respaldo fuera de la PC (RNF-08).
- **Decididos en ADS S14, sin implementar:** ADR-007 (el abono lo crea `Cobro.abonar()`) y ADR-008 (`VentaAnuladaEvent`
  con los datos que sus oyentes necesitan). Se implementan en la integración con LP2.
