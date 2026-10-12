# SGE-JD — Sistema de Gestión Empresarial para JD Refrigeración S.A.C.

**Tipo de proyecto:** Sistema empresarial modular (Spring Boot + Oracle + SPA Angular)
**Cliente:** JD Refrigeración S.A.C. (Lisandro Javier Madrid Laos, propietario)
**Curso:** Proyecto Integrador — ADS / BD2 / LP2, Universidad Peruana Unión (base académica: BomERP)
**Repositorio:** `https://github.com/YWAGNERCS/proyecto-SAC` (backend: rama `master` · frontend: rama `frontend`)
**Última revisión contra el código:** 11/10/2026 (sesión S09 de LP2)

---

## 1. Resumen ejecutivo

JD Refrigeración S.A.C. es una empresa con 20 años de operación en climatización, refrigeración y ventilación forzada. Hoy gestiona todo su negocio (cotizaciones, ventas, compras y facturación) en hojas de cálculo desconectadas entre sí, sin ningún sistema que las integre.

Este proyecto construye un sistema de gestión que centraliza esa operación: un único backend Spring Boot, modular, conectado a Oracle, que expone una API REST consumida por un portal público (catálogo) y un panel administrativo (SPA Angular).

No es un ERP genérico. Es una base académica con alcance acotado al flujo comercial real del cliente, extensible, que sigue el estándar de arquitectura modular del curso (un solo ejecutable, módulos delimitados por paquete, sin microservicios).

---

## 2. El problema del negocio

| # | Problema identificado | Impacto |
|---|---|---|
| 1 | Cotizaciones en Excel desconectadas del registro de ventas y de SUNAT | Doble digitación, errores de decimales, tiempo operativo perdido |
| 2 | Sin seguimiento de mantenimiento preventivo post-venta | Pérdida de oportunidades comerciales recurrentes con clientes corporativos (70% de la cartera) |
| 3 | Precios en soles y dólares calculados a mano, con margen variable sobre tipo de cambio | Riesgo de fijar precios de venta incorrectos |
| 4 | Información dispersa en disco duro local, sin respaldo | Pérdida de historial documentada por el propio cliente |
| 5 | Sin presencia digital | Alcance comercial limitado a referidos y contacto directo |
| 6 | Sin control de acceso ni trazabilidad de quién hace qué | Imposible auditar operaciones con más de un usuario administrativo |

**Pregunta de diseño que resuelve el proyecto:** ¿cómo centralizar cotización → venta → facturación → postventa en un solo sistema, sin que el cliente tenga que cambiar radicalmente su forma de trabajar (WhatsApp y correo como canales, operación desde una sola sede, modelo de precio por margen)?

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
    ├── cotizaciones/               # cabecera-detalle: Cotizacion-ItemCotizacion (+ PDF)
    ├── ventas/                     # cabecera-detalle: Venta-DetalleVenta (+ anulación)
    ├── compras/                    # cabecera-detalle: Compra-DetalleCompra
    ├── inventario/                 # alertas de stock mínimo
    ├── facturacion/                # comprobantes electrónicos (OSE/SUNAT, simulado)
    ├── mantenimiento/              # postventa, alertas automáticas
    ├── notificaciones/             # correo (real) y WhatsApp (simulado)
    ├── reportes/                   # consultas agregadas, sin lógica de negocio propia
    ├── seguridad/                  # usuarios, roles, autenticación
    └── config/                     # SecurityConfig (seguridad + CORS), manejo global de errores

bomerp-frontend/                    # SPA Angular 22 (rama frontend)
└── src/app/
    ├── core/                       # auth (servicio, guard), interceptores, layout
    └── features/                   # catalogo, clientes, proveedores, cotizaciones, ventas, compras, auth
```

**Reglas de módulo:**
- Cada paquete administra sus propios repositorios y entidades; ningún módulo accede directamente al repositorio de otro. *Hoy se incumple en `facturacion` y `reportes` (ver README, sección 5).*
- Un solo `DataSource` y un solo esquema Oracle (`JDREFRIGERACION`). El curso propone un esquema por módulo (`BOM_CATALOGO`, `BOM_VENTAS`…), pendiente de decidir.
- La comunicación entre módulos es por servicios Java inyectados (`@Service`), nunca por llamadas HTTP internas ni Feign.
- API versionada: el panel administrativo usa `/api/v1/admin/**` y el catálogo público `/api/v1/publico/**` (sin login).
- Errores uniformes: 400 datos inválidos, 404 no encontrado, 409 regla de negocio o stock insuficiente.

---

## 4. Módulos: qué hace cada uno y por qué existe

Los 12 módulos de dominio se agrupan en 4 tipos:

1. **Maestros (catálogos base):** guardan la información central que alimenta al resto del sistema.
2. **Transaccionales (cabecera-detalle):** manejan las operaciones comerciales atómicas (el núcleo).
3. **Operativos y de soporte:** aplican reglas de negocio posteriores a la venta o transversales al stock.
4. **De integración y transversales:** conectan el sistema con el exterior o protegen la aplicación.

| Módulo | Tipo | Responsabilidad | Entidad(es) |
|---|---|---|---|
| **catalogo** | Maestro | Productos/servicios, categorías, precio con margen % sobre costo, multimoneda, catálogo público sin costo ni margen | `Producto`, `Categoria` |
| **clientes** | Maestro | Datos maestros de clientes, autocompletado por RUC/DNI | `Cliente` |
| **proveedores** | Maestro | Datos maestros de proveedores | `Proveedor` |
| **cotizaciones** | Transaccional | Elaboración y seguimiento de cotizaciones, PDF, envío por WhatsApp/correo, aceptar/rechazar | `Cotizacion` – `ItemCotizacion` |
| **ventas** | Transaccional | Venta directa o desde cotización aceptada, contado/crédito, cobranza, anulación con restauración de stock | `Venta` – `DetalleVenta` |
| **compras** | Transaccional | Compras a proveedor, con incremento automático de stock | `Compra` – `DetalleCompra` |
| **inventario** | Soporte | Alertas de stock mínimo (tarea diaria 8:00 a. m.) | — (transversal a `catalogo`) |
| **facturacion** | Integración | Comprobante electrónico (XML UBL 2.1) e integración con un OSE (hoy simulado) | `Comprobante` |
| **mantenimiento** | Operativo | 4 mantenimientos preventivos (3/6/9/12 meses) tras vender equipos de climatización o refrigeración; alertas diarias | `Mantenimiento` |
| **notificaciones** | Integración | Interfaces segregadas por canal: `NotificadorCorreo` (SMTP real) y `NotificadorWhatsApp` (simulado) | — |
| **reportes** | Soporte | Ventas por periodo, cobranza (pendiente vs. atrasada), cotizaciones pendientes | — (solo lectura agregada) |
| **seguridad** | Transversal | Usuarios, roles (`ADMIN`, `ASISTENTE`), autenticación HTTP Basic + BCrypt (JWT previsto en S10) | `Usuario` |

**Reglas de negocio clave de ventas:**
- El servidor fija el precio unitario y calcula subtotal, IGV (18%) y total; el cliente solo envía producto y cantidad.
- Solo los materiales (`MATERIAL_SERVICIO`) controlan stock: si no alcanza → 409 y se revierte toda la venta. Los equipos no controlan stock.
- Una cotización solo se convierte si está ACEPTADA, y una sola vez (409).
- Ciclo de vida de la venta: `REGISTRADA → ANULADA` (`PATCH /ventas/{id}/anular`), separado del estado de cobro (`PENDIENTE/PAGADO/ATRASADO`). Anular dos veces → 409; al anular se devuelve el stock de los materiales.

**Nota de alineamiento con el curso:** el corte base exigido es `catalogo` + `ventas` funcionales, con `inventario`, `compras` y `seguridad` delimitados. Este proyecto va más allá (cotizaciones, facturación, mantenimiento y reportes), porque son requerimientos reales confirmados por el cliente.

---

## 5. Decisiones de arquitectura relevantes

- **Monolito modular, no microservicios:** 2 usuarios administrativos, una sola sede y bajo volumen de transacciones. Los microservicios resolverían un problema de escala que este negocio no tiene y solo añadirían costo operativo.
- **DTOs de salida separados de las entidades JPA:** el catálogo público nunca expone el costo base ni el margen interno, solo el precio final calculado.
- **Interfaces segregadas para integraciones externas** (`ConsultaRucClient`, `OseClient`, `NotificadorCorreo`/`NotificadorWhatsApp`): permiten cambiar de proveedor sin tocar la lógica de negocio. Hoy se elige la implementación con `@Primary`.
- **Cabecera-detalle como patrón transversal:** `Cotizacion/ItemCotizacion`, `Venta/DetalleVenta` y `Compra/DetalleCompra` siguen la misma forma, con atomicidad (commit/rollback) en cada operación.
- **Secretos fuera del código:** se leen de variables de entorno o de `application-local.properties` (ignorado por git).
- **Seguridad en dos etapas:** HTTP Basic como solución transitoria; JWT con roles en S10 (backend) y S11 (frontend), según el ADR-004 del curso.

---

## 6. Estado actual

| Área | Estado |
|---|---|
| Módulos backend (los 12) | ✅ Operativos; integraciones RUC/DNI, OSE y WhatsApp **simuladas** |
| Facturación electrónica | ⚠️ OSE real pendiente; correlativo aleatorio (SUNAT exige secuencial) |
| Frontend / SPA | 🔄 Angular 22 en desarrollo: login, layout, categorías, clientes, proveedores, cotizaciones, compras y ventas (S09: formulario cabecera-detalle, reporte filtrable y anulación) |
| Seguridad | ⚠️ Login implementado, pero la protección de `/api/v1/admin/**` está **desactivada** (`app.security.admin-requiere-login=false`); roles sin uso; JWT pendiente (S10) |
| Modelo relacional Oracle | ✅ Corregido (relaciones cabecera-detalle con dirección de FK validada). ⚠️ `Producto` aún sin relación con `Categoria` |
| Credenciales | ✅ Externalizadas. ⚠️ Confirmar que se revocaron las que quedaron en el historial de git |
| Control de versiones | ✅ GitHub compartido (`YWAGNERCS/proyecto-SAC`) |
| Pruebas automatizadas | ⚠️ Mínimas (sin pruebas de ventas ni de módulos) |

La lista completa de inconsistencias y pendientes está en `README.md`, sección **5. Por verificar**.

---

## 7. Cómo ejecutar

1. JDK 21, Maven y Docker Desktop.
2. Oracle 23 Free en Docker (`docker-compose.yml` en la carpeta raíz del workspace): `docker start SGE-JD` si el contenedor ya existe; si no, `docker volume create sge-jd-oracle-data` y `docker compose up -d`. Puerto **1522**, servicio `FREEPDB1`, usuario `jdrefrigeracion`.
3. Credenciales: copiar `application-local.properties.example` como `application-local.properties` y completar `spring.datasource.password` (o usar las variables `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `DECOLECTA_TOKEN`, `CORREO_OFICINA`).
4. `mvn spring-boot:run` (puerto 8080). Swagger UI: `/swagger-ui.html`.
5. Verificar: `GET /api/v1/publico/catalogo` (sin login) y `GET /api/v1/admin/auth/me` con usuario y contraseña (HTTP Basic).
6. Frontend: en `bomerp-frontend`, `npm install` y `npm start` → `http://localhost:4200`.

Usuarios de desarrollo: `admin` / `asistente`, contraseña `123456`.

---

## 8. Equipo

| Integrante | Módulos a cargo |
|---|---|
| Yoel Wagner Chambi Sirena | Cotizaciones, coordinación con el cliente |
| Aron Rodrigo | Ventas, Facturación |
| Ronald Apaza | Compras, Proveedores, Inventario |
