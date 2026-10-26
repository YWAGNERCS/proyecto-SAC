# SGE-JD — Sistema de Gestión Empresarial para JD Refrigeración S.A.C.

**Tipo de proyecto:** Sistema empresarial modular (Spring Boot + Oracle + SPA Angular)
**Cliente:** JD Refrigeración S.A.C. (Lisandro Javier Madrid Laos, propietario)
**Curso:** Proyecto Integrador — ADS / BD2 / LP2, Universidad Peruana Unión (base académica: BomERP)
**Repositorio:** `https://github.com/YWAGNERCS/proyecto-SAC` (backend: rama `master` · frontend: rama `frontend`)
**Equipo:** CherryTree
**Última revisión contra el código:** 25/10/2026 (sesión S12 de LP2, cierre de la Unidad II)
**Documentación publicada:** https://ywagnercs.github.io/proyecto-SAC/

---

## 1. Resumen ejecutivo

JD Refrigeración S.A.C. es una empresa con 20 años de operación en climatización, refrigeración y ventilación forzada. Hoy gestiona todo su negocio (proformas, ventas, compras y facturación) en hojas de cálculo desconectadas entre sí, sin ningún sistema que las integre.

Este proyecto construye un sistema de gestión que centraliza esa operación: un único backend Spring Boot, modular, conectado a Oracle, que expone una API REST consumida por un portal público (catálogo) y un panel administrativo (SPA Angular).

No es un ERP genérico. Es una base académica con alcance acotado al flujo comercial real del cliente, extensible, que sigue el estándar de arquitectura modular del curso (un solo ejecutable, módulos delimitados por paquete, sin microservicios).

---

## 2. El problema del negocio

| # | Problema identificado | Impacto |
|---|---|---|
| 1 | Proformas en Excel desconectadas del registro de ventas y de SUNAT | Doble digitación, errores de decimales, tiempo operativo perdido |
| 2 | Sin seguimiento de mantenimiento preventivo post-venta | Pérdida de oportunidades comerciales recurrentes con clientes corporativos (70% de la cartera) |
| 3 | Precios en soles y dólares calculados a mano, con margen variable sobre tipo de cambio | Riesgo de fijar precios de venta incorrectos |
| 4 | Información dispersa en disco duro local, sin respaldo | Pérdida de historial documentada por el propio cliente |
| 5 | Sin presencia digital | Alcance comercial limitado a referidos y contacto directo |
| 6 | Sin control de acceso ni trazabilidad de quién hace qué | Imposible auditar operaciones con más de un usuario administrativo |

**Pregunta de diseño que resuelve el proyecto:** ¿cómo centralizar proforma → venta → facturación → postventa en un solo sistema, sin que el cliente tenga que cambiar radicalmente su forma de trabajar (WhatsApp y correo como canales, operación desde una sola sede, modelo de precio por margen)?

---

## 3. Arquitectura

Monolito modular: **un solo proyecto Maven, un solo ejecutable Spring Boot**, organizado en paquetes de módulo sobre una única base Oracle. Sin reactor multi-módulo, sin microservicios y sin comunicación HTTP interna: los módulos se comunican a través de servicios Java públicos.

```
bomerp-backend/                     # proyecto Maven único
└── src/main/java/com/bomerp/
    ├── BomerpApplication.java      # único Spring Boot ejecutable
    ├── catalogo/                   # productos, categorías, catálogo público
    ├── clientes/                   # datos maestros + autocompletado RUC/DNI
    ├── proveedores/                # datos maestros de proveedores
    ├── proformas/                  # cabecera-detalle: Proforma-ItemProforma (+ PDF); antes "cotizaciones"
    ├── ventas/                     # cabecera-detalle: Venta-DetalleVenta (+ anulación)
    ├── compras/                    # cabecera-detalle: Compra-DetalleCompra
    ├── inventario/                 # alertas de stock mínimo
    ├── facturacion/                # comprobantes electrónicos (OSE/SUNAT, simulado)
    ├── postventa/                  # mantenimientos preventivos, agenda y alertas automáticas
    ├── cobranza/                   # cobros (abonos) y saldos de las ventas a crédito
    ├── notificaciones/             # correo (real) y WhatsApp (simulado)
    ├── reportes/                   # TRANSITORIO: consultas que deben pasar a ventas/, proformas/ y cobranza/
    ├── (seguridad)                 # RETIRADO en S10b: la identidad la maneja Keycloak (docker-compose)
    └── config/                     # SecurityConfig (seguridad + CORS), manejo global de errores

bomerp-frontend/                    # SPA Angular 22 (rama frontend)
└── src/app/
    ├── core/                       # auth (servicio, guard), interceptores, layout
    └── features/                   # catalogo, clientes, proveedores, proformas, ventas, compras, cobranza, postventa
```

**Reglas de módulo:**
- Cada paquete administra sus propios repositorios y entidades; ningún módulo accede directamente al repositorio de otro. *Hoy se incumple en `facturacion` y `reportes` (ver "Por verificar" en la documentación completa).*
- Un solo `DataSource` y un solo esquema Oracle (`JDREFRIGERACION`). El curso propone un esquema por módulo (`BOM_CATALOGO`, `BOM_VENTAS`…), pendiente de decidir.
- La comunicación entre módulos es por servicios Java inyectados (`@Service`), nunca por llamadas HTTP internas ni Feign.
- API versionada: el panel administrativo usa `/api/v1/admin/**` y el catálogo público `/api/v1/publico/**` (sin login).
- Errores uniformes: 400 datos inválidos, 404 no encontrado, 409 regla de negocio o stock insuficiente.

---

## 4. Módulos: qué hace cada uno y por qué existe

El sistema tiene **12 módulos**, agrupados en 4 tipos:

1. **Maestros (catálogos base):** guardan la información central que alimenta al resto del sistema.
2. **Transaccionales (cabecera-detalle):** manejan las operaciones comerciales atómicas (el núcleo).
3. **Operativos y de soporte:** aplican reglas de negocio posteriores a la venta o transversales al stock.
4. **De integración y transversales:** conectan el sistema con el exterior o protegen la aplicación.

| Módulo | Tipo | Responsabilidad | Entidad(es) |
|---|---|---|---|
| **catalogo** | Maestro | Productos/servicios, categorías, precio con margen % sobre costo, multimoneda, catálogo público sin costo ni margen | `Producto`, `Categoria` |
| **clientes** | Maestro | Datos maestros de clientes, autocompletado por RUC/DNI | `Cliente` |
| **proveedores** | Maestro | Datos maestros de proveedores | `Proveedor` |
| **proformas** | Transaccional | Elaboración y seguimiento de proformas, PDF, envío por WhatsApp/correo, aceptar/rechazar | `Proforma` – `ItemProforma` |
| **ventas** | Transaccional | Venta directa o desde proforma aceptada, contado/crédito, anulación con restauración de stock (y devolución pendiente si el cliente ya pagó) | `Venta` – `DetalleVenta` |
| **compras** | Transaccional | Compras a proveedor, con incremento automático de stock | `Compra` – `DetalleCompra` |
| **inventario** | Soporte | Alertas de stock mínimo (tarea diaria 8:00 a. m.) | — (transversal a `catalogo`) |
| **facturacion** | Integración | Comprobante electrónico (XML UBL 2.1) e integración con un OSE (hoy simulado) | `Comprobante` |
| **postventa** | Operativo | 4 mantenimientos preventivos (3/6/9/12 meses) tras vender equipos de climatización o refrigeración; alertas diarias a la oficina | `Mantenimiento` |
| **cobranza** | Transaccional | Cuentas por cobrar: saldo por venta, abonos parciales o totales, historial de cobros y devoluciones de ventas anuladas | `Cobro` – `DetalleCobro` (un cobro puede abonar a varias ventas del mismo cliente y moneda), `Devolucion` |
| **notificaciones** | Integración | Interfaces segregadas por canal: `NotificadorCorreo` (SMTP real) y `NotificadorWhatsApp` (simulado) | — |
| **seguridad** | Transversal | Usuarios, roles y login en **Keycloak** (realm `bomerp`, roles ADMIN y ASISTENTE). El backend solo valida el JWT (`config/SecurityConfig`, `config/PerfilController`) | — (vive en Keycloak) |

**Reportes por módulo (decisión del equipo):** no existe un módulo `reportes`; cada módulo expone sus propias consultas y reportes, como propone el curso (p. ej. `GET /api/v1/ventas/resumen` en la S09). Así ningún módulo necesita leer los repositorios de otro.
- **ventas:** reporte filtrable por estado y fechas (ya en `GET /ventas`) y resumen por periodo.
- **cobranza:** pendientes y atrasadas.
- **proformas:** proformas pendientes.
- *Transitorio:* `ventas-resumen`, `cobranza` y `proformas-pendientes` siguen en el paquete `reportes` y usan repositorios ajenos; falta moverlos.

**Reglas de negocio clave de ventas:**
- El servidor fija el precio unitario y calcula subtotal, IGV (18%) y total; el cliente solo envía producto y cantidad.
- Solo los materiales (`MATERIAL_SERVICIO`) controlan stock: si no alcanza → 409 y se revierte toda la venta. Los equipos no controlan stock.
- Una proforma solo se convierte si está ACEPTADA, y una sola vez (409).
- Ciclo de vida de la venta: `REGISTRADA → ANULADA` (`PATCH /ventas/{id}/anular`), separado del estado de cobro (`PENDIENTE/PAGADO/ATRASADO`). Anular dos veces → 409. Al anular, en una sola transacción: se devuelve el stock de los materiales, **Postventa** cancela los mantenimientos pendientes, **Facturación** emite la nota de crédito del comprobante aceptado y **Cobranza** deja una devolución pendiente si el cliente ya había pagado (evento `VentaAnuladaEvent`, para que Ventas no dependa de Cobranza). Una venta solo pasa a PAGADO por sus abonos.
- Las ventas anuladas no cuentan en los reportes de ventas ni en la cobranza.

- Moneda: cada venta/proforma elige soles o dólares. Los precios de productos en la otra moneda se convierten con el tipo de cambio (SUNAT del día, editable), que queda guardado; al convertir una proforma en venta se respeta su tipo de cambio (el cliente paga lo cotizado).

**Nota de alineamiento con el curso:** el corte base exigido es `catalogo` + `ventas` funcionales, con `inventario`, `compras` y `seguridad` delimitados. Este proyecto va más allá (proformas, facturación, postventa y notificaciones), porque son requerimientos reales confirmados por el cliente.

---

## 5. Decisiones de arquitectura relevantes

- **Monolito modular, no microservicios:** 2 usuarios administrativos, una sola sede y bajo volumen de transacciones. Los microservicios resolverían un problema de escala que este negocio no tiene y solo añadirían costo operativo.
- **DTOs de salida separados de las entidades JPA:** el catálogo público nunca expone el costo base ni el margen interno, solo el precio final calculado.
- **Interfaces segregadas para integraciones externas** (`ConsultaRucClient`, `OseClient`, `NotificadorCorreo`/`NotificadorWhatsApp`): permiten cambiar de proveedor sin tocar la lógica de negocio. Hoy se elige la implementación con `@Primary`.
- **Cabecera-detalle como patrón transversal:** `Proforma/ItemProforma`, `Venta/DetalleVenta`, `Compra/DetalleCompra` y `Cobro/DetalleCobro` siguen la misma forma, con atomicidad (commit/rollback) en cada operación; en la SPA, los cuatro formularios usan `FormArray`.
- **Secretos fuera del código:** el backend los lee de variables de entorno o de `application-local.properties`, y Docker y Keycloak de `.env` (ambos ignorados por git, con plantilla `.example`).
- **Seguridad con Keycloak (hecho en S10b, 18/10/2026):** Keycloak emite los tokens y el backend los valida como Resource Server; vendedorId = `sub` del token.
- **Reportes dentro de cada módulo:** evita un módulo "de lectura" que dependa de los repositorios de todos los demás y respeta los límites entre módulos.
- **"Postventa" en lugar de "mantenimiento":** el módulo cubre todo lo que pasa después de la venta (mantenimientos preventivos, garantías y seguimiento del cliente), no solo el mantenimiento técnico.
- **"Proforma" en lugar de "cotización":** es el término que usa el cliente para el documento previo a la venta. Se renombró en código, base de datos (tablas `PROFORMAS`, `ITEMS_PROFORMA`) y SPA.
- **Cobranza como módulo propio:** el seguimiento de lo que se debe (crédito, atrasos, pagos) es un proceso distinto de vender; separarlo de ventas permite registrar pagos parciales y recordatorios.

---

## 6. Estado actual

| Área | Estado |
|---|---|
| Módulos backend (los 12) | ✅ Operativos; integraciones OSE y WhatsApp **simuladas**; RUC/DNI real vía apis.net.pe v1 (respaldo opcional: Decolecta) |
| Reorganización | ✅ Cotizaciones → **Proformas** y Mantenimiento → **Postventa** (código, BD y SPA; migración en `migracion_proforma.sql`). ✅ **Cobranza** separada de ventas (15/10/2026). ⏳ Repartir el paquete `reportes` |
| Facturación electrónica | ⚠️ OSE real pendiente. ✅ Correlativo secuencial por serie (F001, B001, FC01, BC01) y datos reales de JD en el PDF |
| Frontend / SPA | ✅ Angular 22: login con Keycloak, catálogo, clientes, proveedores, proformas, ventas, compras, cobranza (con devoluciones) y postventa. **Diseño responsivo** (celular, tablet y escritorio). Faltan pantallas de alertas de inventario y de facturación |
| Seguridad | ✅ Keycloak 26.8 + Postgres; `/api/v1/admin/**` exige JWT; reglas por rol verificadas (matriz de 14 casos). ✅ S11: la SPA inicia sesión con Authorization Code + PKCE (sin formulario propio), guards por sesión y por rol, refresco del token ante 401. ✅ Protección contra fuerza bruta y política de contraseñas (25/10/2026) |
| Modelo relacional Oracle | ✅ Corregido (relaciones cabecera-detalle con dirección de FK validada). ✅ `Producto` → `Categoria` (13/10/2026; hoy ningún producto queda sin categoría). Tablas `NOTAS_CREDITO` y `DEVOLUCIONES` |
| Credenciales | ✅ Externalizadas en `.env` y `application-local.properties`; las de desarrollo se cambiaron el 25/10/2026. ⚠️ Confirmar que se revocaron la contraseña de Gmail y el token de Decolecta que quedaron en el historial de git |
| Control de versiones | ✅ GitHub compartido (`YWAGNERCS/proyecto-SAC`, con topics); documentación publicada en https://ywagnercs.github.io/proyecto-SAC/ |
| Operación | ✅ `scripts/`: arranque, apagado, respaldo, restauración y base limpia de un clic |
| Pruebas automatizadas | ⚠️ Mínimas (sin pruebas de ventas ni de módulos) |

La lista completa de inconsistencias y pendientes está en la documentación completa (`src/main/resources/static/docs/README.md`), sección **5. Por verificar**.

---

## 7. Cómo ejecutar

**Uso diario:** doble clic en `scripts/iniciar_sgejd.bat` (enciende Docker, Oracle, Keycloak, backend y SPA). Primera instalación:

1. JDK 21, Maven y Docker Desktop. Copiar `.env.example` como `.env` y poner las contraseñas.
2. Oracle 23 Free y Keycloak en Docker (`docker-compose.yml` en `bomerp-backend`): `docker start SGE-JD` si el contenedor ya existe; si no, `docker volume create sge-jd-oracle-data` y `docker compose up -d`. Puerto **1522**, servicio `FREEPDB1`, usuario `jdrefrigeracion`. Identidad: `docker compose up -d keycloak-db keycloak` (puerto **38080**) y luego `python keycloak/configurar_realm.py`.
3. Credenciales: copiar `application-local.properties.example` como `application-local.properties` y completar `spring.datasource.password` (o usar las variables `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `DECOLECTA_TOKEN`, `CORREO_OFICINA`).
4. `mvn spring-boot:run` (puerto 8080). Swagger UI: `/swagger-ui.html`.
5. Verificar: `GET /api/v1/publico/catalogo` (sin login) y `pruebas-seguridad/05_matriz.ps1` (tokens de Keycloak: `lisandro` / `lucy`).
6. Frontend: en `bomerp-frontend`, `npm install` y `npm start` → `http://localhost:4200`.

Usuarios de desarrollo: `lisandro` (ADMIN) · `lucy` (ASISTENTE) (cuentas de Keycloak; las contraseñas están en `bomerp-backend/.env` (no se versiona; plantilla en `.env.example`)).

---

## 8. Equipo

Equipo **CherryTree**.

| Integrante | Módulos a cargo | Transaccional (cabecera-detalle) |
|---|---|---|
| Yoel Wagner Chambi Sirena | Proveedores, Proformas, Ventas | Proforma–ItemProforma y Venta–DetalleVenta |
| Wilson Ronald Apaza Mamani | Catálogo, Inventario, Compras, Postventa | Compra–DetalleCompra |
| Aron Rodrigo Magaño Quispe | Clientes, Facturación, Notificaciones, Cobranza | Cobro–DetalleCobro |

- **Seguridad:** resuelta con Keycloak en S10b (realm `bomerp`, roles ADMIN y ASISTENTE) y S11 (login con redirección y PKCE).
- Cada integrante es responsable de su módulo en backend y frontend, y lo evidencia con sus propios commits (el curso evalúa y sustenta el aporte individual; en S09 cada estudiante documenta un formulario cabecera-detalle de su propio módulo).
