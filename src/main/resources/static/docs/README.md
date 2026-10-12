# DOCUMENTACIÓN DEL SISTEMA WEB "JD REFRIGERACIÓN" (SGE-JD / BomERP)

> Última revisión contra el código: **11/10/2026** (sesión S09 de LP2).
> Repositorio: `https://github.com/YWAGNERCS/proyecto-SAC` — backend en la rama `master`, frontend en la rama `frontend`.
> Lo que no coincide entre el código, esta documentación o la guía del curso está listado en **[5. Por verificar](#por-verificar)**.

---

## Índice
1. [Documentación funcional](#doc-funcional)
2. [Documentación técnica](#doc-tecnica)
3. [Manual de usuario](#manual-usuario)
4. [Estado y trazabilidad del proyecto](#estado-trazabilidad)
5. [Por verificar](#por-verificar)

---

<a id="doc-funcional"></a>

## 1. DOCUMENTACIÓN FUNCIONAL (¿Qué hace el sistema?)

### Objetivo general
Desarrollar e implementar un sistema integral web (ERP/CRM) para JD Refrigeración S.A.C., que centralice y automatice el catálogo de productos, el registro de clientes/proveedores, el flujo completo de cotizaciones y ventas, y la posventa (mantenimientos preventivos), mejorando la eficiencia operativa y la atención al cliente.

### Alcance del proyecto

**Incluye:**
- Catálogo de productos con soporte multimoneda (soles/dólares) y precio de venta calculado por margen porcentual sobre el costo.
- Clientes y Proveedores con autocompletado por RUC/DNI (hoy con un cliente **simulado**, ver 5).
- Cotizaciones con exportación a PDF y envío por correo (SMTP).
- Ventas al contado o a crédito: venta directa (cabecera-detalle) y conversión desde una cotización aceptada; anulación con restauración de stock.
- Facturación electrónica (simulada, a la espera de credenciales productivas de un OSE).
- Posventa: mantenimientos preventivos con alertas automáticas (tarea programada diaria).
- Inventario: descuento al vender, incremento al comprar y alertas de stock mínimo.
- Consultas y reportes dentro de cada módulo: ventas (reporte filtrable, resumen por periodo, cobranza) y cotizaciones (pendientes).
- Panel administrativo web (SPA Angular, en `bomerp-frontend`) con inicio de sesión.

**Queda explícitamente fuera del alcance:**
- Módulo de planillas y recursos humanos.
- Pasarelas de pago online (las ventas se registran como pagadas o a crédito, sin procesar pagos reales).
- Control de GPS de los técnicos de mantenimiento.

### Módulos (backend)

| # | Módulo | Tipo | Responsable | Estado |
|---|---|---|---|---|
| 1 | Catálogo (productos, categorías, catálogo público) | Maestro | Ronald | ✅ (ver 5: falta CRUD completo de producto y relación con categoría) |
| 2 | Clientes (autocompletado RUC/DNI) | Maestro | Aron | ⚠️ Autocompletado simulado |
| 3 | Proveedores | Maestro | Yoel | ✅ |
| 4 | Cotizaciones (+ PDF, + envío por correo) | Transaccional | Yoel | ✅ |
| 5 | Ventas (directa, desde cotización, crédito, anulación) | Transaccional | Yoel | ✅ |
| 6 | Compras (incrementa stock) | Transaccional | Ronald | ✅ |
| 7 | Inventario (alertas de stock mínimo) | Soporte | Ronald | ✅ |
| 8 | Posventa (cronograma preventivo automático; paquete `mantenimiento`) | Operativo | Yoel | ✅ (renombrar paquete pendiente) |
| 9 | Facturación (Comprobante, XML UBL 2.1) | Integración | Aron | ⚠️ Simulado — falta OSE real (Nubefact) y correlativo real |
| 10 | Notificaciones (SMTP real + WhatsApp) | Integración | Aron | ⚠️ WhatsApp simulado |
| 11 | Seguridad (usuarios, roles, login) | Transversal | Aron | ⚠️ Login HTTP Basic; protección **desactivada** por configuración y roles sin uso (ver 5) |
| — | Frontend (SPA Angular 22) | — | Cada responsable en su módulo | 🔄 En desarrollo (`bomerp-frontend`, rama `frontend`) |

> **Reportes:** no son un módulo. Cada módulo expone sus consultas (decisión del equipo, alineada con el curso). Hoy `ventas-resumen`, `cobranza` y `cotizaciones-pendientes` siguen en el paquete transitorio `reportes` (ver 5).

### Requerimientos Funcionales (RF)

| RF | Descripción | Estado real en el código |
|---|---|---|
| RF-01 | **Autenticación:** inicio de sesión con credenciales seguras. | ✅ HTTP Basic + BCrypt; pantalla de login en la SPA. |
| RF-02 | **Roles y permisos:** restringir módulos según el rol (Administrador, Asistente). | ❌ Los roles `ADMIN`/`ASISTENTE` existen, pero ningún endpoint ni pantalla los usa. Planificado para S10–S11 (JWT). |
| RF-03 | **Catálogo de productos:** moneda dual y precio por margen porcentual. | ✅ Precio = costo base × (1 + margen %). Solo hay listado de productos (sin crear/editar/eliminar por API). |
| RF-04 | **Autocompletado de clientes/proveedores** por RUC/DNI vía API externa. | ⚠️ Existen clientes para apis.net.pe y Decolecta, pero el que se inyecta es el **simulado** (`@Primary`). |
| RF-05 | **Cotizaciones** con cálculo automático de IGV y subtotales. | ✅ |
| RF-06 | **PDF** formal y corporativo de la cotización. | ✅ Se genera al **enviar** la cotización (no al guardar). |
| RF-07 | **Envío de cotizaciones por correo** (SMTP). | ✅ `POST /cotizaciones/{id}/enviar`; requiere credenciales SMTP configuradas. |
| RF-08 | **Conversión de cotización aceptada en venta** sin redigitar. | ✅ Valida que esté ACEPTADA y que no se haya convertido antes (409). |
| RF-09 | **Control de créditos** (contado/crédito, deudas pendientes y atrasadas). | ✅ |
| RF-10 | **Facturación electrónica** (estructura UBL 2.1). | ⚠️ Simulada; el número del comprobante es aleatorio. |
| RF-11 | **Descuento de stock** atómico al vender. | ✅ Solo materiales (`MATERIAL_SERVICIO`); si no alcanza → 409 y no se guarda nada. Los equipos no controlan stock. |
| RF-12 | **Incremento de stock** al registrar compras. | ✅ |
| RF-13 | **Alertas de stock mínimo** diarias. | ✅ 8:00 a. m., por correo a `app.notificaciones.correo-oficina` (si está vacío, no se envía). |
| RF-14 | **Posventa: programación de 4 mantenimientos** tras vender equipos. | ✅ A los 3, 6, 9 y 12 meses, para equipos de CLIMATIZACIÓN y REFRIGERACIÓN. |
| RF-15 | **Posventa: alertas de mantenimiento** (8:00 a. m., próximos 7 días, correo a la oficina). | ✅ |
| RF-16 | **Reportes** de ingresos y cuentas por cobrar, segmentados por moneda (dentro del módulo ventas). | ⚠️ Segmenta soles/dólares, pero incluye ventas anuladas (ver 5). |
| RF-17 | **Anulación de venta** (nuevo, S09): REGISTRADA → ANULADA con restauración de stock. | ✅ `PATCH /ventas/{id}/anular`; 409 si ya estaba anulada. |

### Requerimientos No Funcionales (RNF)

- **RNF-01 (Arquitectura):** monolito modular en capas (controladores, servicios, repositorios), un módulo por paquete. *Hoy hay 2 módulos que usan repositorios de otros (ver 5).*
- **RNF-02 (Rendimiento):** endpoints por debajo de 1000 ms con carga estándar. *Objetivo, aún sin medición formal.*
- **RNF-03 (Interoperabilidad):** backend desacoplado del frontend, solo API REST en JSON, versionada en `/api/v1/...`.
- **RNF-04 (Mantenibilidad e IoC):** inyección de dependencias de Spring; credenciales externalizadas en variables de entorno o `application-local.properties` (ignorado por git). ✅ Cumplido en el archivo; ver 5 sobre el historial de git.
- **RNF-05 (Seguridad):** `/api/v1/admin/**` protegido con HTTP Basic y contraseñas con BCrypt. *El código lo soporta, pero hoy está desactivado (`app.security.admin-requiere-login=false`).*

### Casos de uso / Historias de usuario

- **CU-01 (Cotizar):** el vendedor selecciona cliente y productos; el sistema calcula IGV y totales; luego envía el PDF al cliente por correo.
- **CU-02 (Posventa: alertas de mantenimiento):** el sistema revisa a diario, a las 8:00 a. m., los mantenimientos de los próximos 7 días y envía un correo a la oficina con los clientes a contactar.
- **CU-03 (Vender):** el vendedor registra una venta directa con varias líneas de producto, revisa el total y confirma; el sistema descuenta stock, programa mantenimientos y emite el comprobante.
- **CU-04 (Anular venta):** el usuario anula una venta registrada; el sistema devuelve al inventario lo vendido y la venta deja de contar en el reporte de la SPA.

---

<a id="doc-tecnica"></a>

## 2. DOCUMENTACIÓN TÉCNICA (¿Cómo está construido?)

### Stack tecnológico (según `pom.xml` y `package.json`)

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot **3.3.4** (Spring Web, Data JPA, Validation, Security, Mail) |
| ORM | Hibernate 6.5 (`ddl-auto=update`) |
| Base de datos | Oracle Database 23 Free (Docker `gvenzl/oracle-free:23-slim`, servicio `FREEPDB1`, puerto **1522**) |
| Seguridad | Spring Security: HTTP Basic + BCrypt (`DelegatingPasswordEncoder`, hashes con prefijo `{bcrypt}`) |
| Documentación API | springdoc-openapi 2.6.0 (Swagger UI en `/swagger-ui.html`) |
| Otras | OpenPDF 1.3.32, Lombok, driver `ojdbc11`; H2 incluido como dependencia (sin perfil configurado) |
| Frontend | Angular 22 (standalone, signals, zoneless), en `bomerp-frontend` |

### Arquitectura de software

Monolito modular: **un solo proyecto Maven y un solo ejecutable Spring Boot**. Cada módulo es un paquete bajo `com.bomerp` con capas internas:

1. **Controllers**: endpoints REST (`/api/v1/admin/...`, `/api/v1/publico/...`).
2. **Services**: reglas de negocio y transacciones (`@Transactional`).
3. **Repositories**: interfaces Spring Data JPA.
4. **Integrations**: clientes hacia servicios externos detrás de interfaces (`ConsultaRucClient`, `OseClient`, `NotificadorCorreo`, `NotificadorWhatsApp`).

Errores: `GlobalExceptionHandler` traduce excepciones a 400 (validación), 404 (`RecursoNoEncontradoException`), 409 (`ReglaNegocioException`, `StockInsuficienteException`) y 500 sin filtrar detalles.

### Estructura del código

```text
src/main/java/com/bomerp/
├── BomerpApplication.java
├── catalogo/        categoria/, producto/, productocatalogo/ (catálogo público, DTO sin costo ni margen)
├── clientes/        cliente/, consultaruc/ (RUC/DNI), general/ (entidad Cliente)
├── compras/         compra/, detallecompra/
├── config/          SecurityConfig (seguridad + CORS), GlobalExceptionHandler, excepciones
├── cotizaciones/    cotizacion/, itemcotizacion/, cotizacionrequest/, pdf/
├── facturacion/     comprobante/, facturacion/, nubefact/, ose/, pdf/
├── inventario/      alertas y tarea programada de stock mínimo
├── mantenimiento/   módulo POSVENTA: programación y alertas (renombrar a posventa/: pendiente)
├── notificaciones/  correo (real) y WhatsApp (simulado)
├── proveedores/     proveedor/
├── reportes/        TRANSITORIO: consultas por repartir a ventas/ y cotizaciones/
├── seguridad/       (transversal) Usuario, UsuarioRepository, UserDetailsServiceImpl, AuthController
└── ventas/          venta/, detalleventa/
```

### Modelo de datos (tablas reales, esquema `JDREFRIGERACION`)

| Tabla | Campos clave |
|---|---|
| `PRODUCTOS` | id, nombre, sector, costo_base, margen_porcentaje, moneda, stock_actual, stock_minimo, visible_en_catalogo |
| `CATEGORIAS` | id, nombre (sin relación con `PRODUCTOS`, ver 5) |
| `CLIENTES` / `PROVEEDORES` | id, tipo (empresa/natural), ruc, dni, correo, teléfono |
| `COTIZACIONES` / `ITEMS_COTIZACION` | cabecera-detalle de cotización |
| `VENTAS` | id, cliente_id, cotizacion_id, fecha_venta, tipo_pago, dias_credito, fecha_limite_pago, **estado** (REGISTRADA/ANULADA), estado_pago, moneda, comprobante_id |
| `DETALLE_VENTA` | id, venta_id, producto_id, cantidad, precio_unitario |
| `COMPRAS` / `DETALLE_COMPRA` | cabecera-detalle de compra |
| `COMPROBANTES` | comprobante electrónico simulado |
| `MANTENIMIENTOS` | id, venta_id, fecha_programada, estado, alerta_enviada |
| `USUARIOS` | id, username, password (BCrypt), rol (`ADMIN` / `ASISTENTE`) |

### API REST (v1)

| Recurso | Endpoints |
|---|---|
| Autenticación | `GET /api/v1/admin/auth/me` (valida credenciales y devuelve usuario y roles) |
| Catálogo público | `GET /api/v1/publico/catalogo` (sin login, sin costo ni margen) |
| Categorías | `GET`, `POST /categorias` · `GET`, `PUT`, `DELETE /categorias/{id}` |
| Productos | `GET /productos` |
| Clientes | `GET`, `POST /clientes` · `GET /clientes/autocompletar/ruc/{ruc}` · `GET /clientes/autocompletar/dni/{dni}` |
| Proveedores | `GET`, `POST /proveedores` · `GET`, `PUT`, `DELETE /proveedores/{id}` · autocompletar RUC/DNI |
| Cotizaciones | `GET`, `POST /cotizaciones` · `POST /cotizaciones/{id}/aceptar` · `/rechazar` · `/enviar` |
| Ventas | `GET /ventas?estado=&desde=&hasta=` · `POST /ventas/directa` · `POST /ventas/desde-cotizacion/{id}` · `GET /ventas/pagos-atrasados` · `POST /ventas/{id}/marcar-pagada` · `PATCH /ventas/{id}/anular` |
| Compras | `GET`, `POST /compras` · `GET /compras/{id}` |
| Inventario | `GET /inventario/alertas` |
| Posventa | `GET /mantenimientos` · `POST /mantenimientos/{id}/marcar-realizado` · `POST /mantenimientos/simular-alerta` |
| Facturación | `POST /facturacion/emitir/{ventaId}` · `POST /facturacion/{id}/enviar` |
| Consultas *(transitorio, por mover a ventas y cotizaciones)* | `GET /reportes/ventas-resumen?desde=&hasta=` · `GET /reportes/cobranza` · `GET /reportes/cotizaciones-pendientes` |

*(Todas las rutas sin prefijo van bajo `/api/v1/admin`.)*

### Guía de instalación

1. Instalar **JDK 21**, Maven y Docker Desktop.
2. Base de datos Oracle en Docker (desde la carpeta que contiene `docker-compose.yml`):
   - Primera vez: `docker volume create sge-jd-oracle-data` y luego `docker compose up -d`.
   - Si el contenedor ya existe: `docker start SGE-JD`.
   - Esperar a que `docker logs SGE-JD` muestre `DATABASE IS READY TO USE!`.
3. Credenciales locales: copiar `src/main/resources/application-local.properties.example` como `application-local.properties` (está en `.gitignore`) y completar al menos:
   ```properties
   spring.datasource.password=<APP_USER_PASSWORD del docker-compose>
   ```
   También se aceptan variables de entorno: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `DECOLECTA_TOKEN`, `CORREO_OFICINA`, `CORS_ALLOWED_ORIGINS`.
4. Ejecutar `mvn spring-boot:run` (puerto 8080). Swagger: `http://localhost:8080/swagger-ui.html`.
5. Frontend: en `bomerp-frontend`, `npm install` y `npm start` (puerto 4200).

---

<a id="manual-usuario"></a>

## 3. MANUAL DE USUARIO (¿Cómo se opera?)

### Requisitos de entorno
- Navegador actualizado (Chrome, Firefox o Edge).
- Internet para la consulta de RUC/DNI real, el envío de correos y la facturación electrónica real (hoy simuladas, salvo el correo).

### Acceso al sistema

> ⚠️ Credenciales solo de desarrollo. No deben usarse ni documentarse así en un ambiente real.

- Usuarios existentes hoy en la base local: `admin` (rol ADMIN) y `asistente` (rol ASISTENTE), contraseña `123456`.
- `data.sql` define además `lisandro` y `lucy`, pero ese archivo **no se ejecuta** (`spring.sql.init.mode=never`).

### Flujos de operación

- **Iniciar sesión:** en la SPA (`http://localhost:4200`) con usuario y contraseña; "Salir" cierra la sesión.
- **Registrar cliente B2B:** ingresar el RUC y usar el autocompletado (hoy devuelve datos simulados).
- **Venta directa:** Ventas → Nueva venta → elegir cliente y forma de pago → agregar líneas buscando el producto por nombre (muestra precio y stock) → revisar subtotal, IGV y total → Registrar y confirmar el resumen.
- **Convertir cotización en venta:** la cotización debe estar ACEPTADA; se elige contado o crédito y el sistema descuenta stock, programa mantenimientos y genera el comprobante.
- **Reporte de ventas:** Ventas → filtros por estado y rango de fechas (botón "Buscar"); muestra totales por moneda, ticket promedio y el detalle de cada venta.
- **Anular una venta:** en el reporte, botón "Anular" (solo en ventas REGISTRADAS) → confirmar; el stock de los materiales se restaura.
- **Reportes financieros (API):** `/api/v1/admin/reportes/ventas-resumen`, `/cobranza` y `/cotizaciones-pendientes`.
- **Posventa:** el sistema envía a diario, a las 8:00 a. m., un correo con los clientes a contactar en los próximos 7 días (requiere `CORREO_OFICINA`).

### Preguntas frecuentes

- **"El cliente no recibió el correo":** verificar el correo del cliente (errores como `.con`), las credenciales SMTP (`MAIL_USERNAME`/`MAIL_PASSWORD`) y la salida al puerto 587.
- **"No me deja vender por stock":** los materiales no admiten stock negativo (409 "Stock insuficiente"); registre una compra o ajuste la cantidad. Los equipos no controlan stock.
- **"El backend no arranca: Network Adapter could not establish the connection":** Oracle no está encendido; inicie Docker Desktop y `docker start SGE-JD`.
- **Soporte técnico:** equipo de desarrollo estudiantil (ver 4. Equipo y responsables).

---

<a id="estado-trazabilidad"></a>

## 4. ESTADO Y TRAZABILIDAD DEL PROYECTO

### Fases de ingeniería de software

| # | Fase | Estado |
|---|---|---|
| 1 | Inicio y planificación | ✅ Completo |
| 2 | Levantamiento de información | ✅ Completo (pendiente confirmar con Lucy el formato exacto de envío a SUNAT) |
| 3 | Análisis de requisitos | ✅ 16 RF originales + RF-17 (anulación, S09) |
| 4 | Modelado de procesos (BPMN AS-IS/TO-BE) | ⏳ Pendiente |
| 5 | Análisis del sistema (casos de uso UML) | ⏳ Pendiente formalizar diagrama |
| 6 | Diseño del sistema (C1–C4, SOLID, BD) | ✅ Completo (pendiente diagrama de clases UML formal) |
| 7 | Desarrollo | 🔄 11 módulos backend operativos (3 con integraciones simuladas); reorganización posventa/reportes pendiente; SPA en desarrollo |
| 8 | Pruebas y validación | 🔄 Pruebas manuales de API; pruebas automatizadas mínimas (ver 5) |
| 9 | Implementación/despliegue | ⏳ Pendiente — sin ambiente accesible para el cliente |
| 10 | Documentación técnica | 🔄 Este documento, `CONTEXTO.md` y los `README.md` del backend y del frontend |
| 11 | Manual de usuario | 🔄 Sección 3 (se completará con la SPA) |
| 12 | Trazabilidad (RTM) | ⏳ **Pendiente — siguiente entregable prioritario** |
| 13 | Mantenimiento y evolución | ✅ Git/GitHub en uso (`YWAGNERCS/proyecto-SAC`) |

### Equipo y responsables

| Integrante | Módulos a cargo | Enfoque |
|---|---|---|
| Yoel Wagner Chambi Sirena | Proveedores, Cotizaciones, Ventas, Posventa | Flujo comercial; coordinación con el cliente |
| Wilson Ronald Apaza Mamani | Catálogo, Inventario, Compras | Abastecimiento y stock |
| Aron Rodrigo Magaño Quispe | Clientes, Facturación, Notificaciones, Seguridad | Integraciones y transversales |

### Avance según las sesiones de LP2

| Sesión | Tema | Estado en el proyecto |
|---|---|---|
| S1–S5 | Backend REST, CRUD, relaciones, cabecera-detalle, reportes, CORS | ✅ con observaciones (ver 5: CRUD de producto y Categoría–Producto) |
| S7 | SPA Angular: layout, rutas, servicios HTTP | ✅ |
| S8 | CRUD de tablas dependientes | ✅ CRUD de categorías y registro de compras · ⚠️ clientes y proveedores solo con listado en la SPA |
| S9 | Formulario transaccional cabecera-detalle | ✅ Venta directa con FormArray, reporte filtrable y anulación |
| S10 | Seguridad backend JWT y roles | ⏳ Próxima (hoy HTTP Basic, que será reemplazado) |
| S11 | Seguridad frontend (guards, interceptores, 401/403) | 🔄 Login, guard e interceptor Basic ya existen |

---

<a id="por-verificar"></a>

## 5. Por verificar

Diferencias encontradas al comparar esta documentación, `CONTEXTO.md`, el código y la guía del curso. Ordenadas por importancia.

### Seguridad
1. **La API está abierta sin login.** `application.properties` tiene `app.security.admin-requiere-login=false`, así que cualquiera puede llamar a `/api/v1/admin/**`. La SPA ya tiene login: hay que pasarlo a `true` (o resolverlo con JWT en S10).
2. **Credenciales en el historial de git.** La contraseña de aplicación de Gmail y el token de Decolecta ya no están en `application.properties`, pero siguen en commits anteriores. **Confirmar** que la contraseña se revocó en Google y que el token se regeneró.
3. **Roles sin efecto (RF-02).** `ADMIN` y `ASISTENTE` pueden hacer exactamente lo mismo. Definir con el cliente qué puede hacer la asistente.
4. **Usuarios de prueba.** La documentación anterior hablaba de `lisandro`/`lucy`, pero en la base solo existen `admin`/`asistente` (los de `data.sql` nunca se insertan). Decidir cuáles son los oficiales.
5. **La SPA guarda el token Basic en `localStorage`**, que equivale a guardar la contraseña. Se resuelve al migrar a JWT (S10–S11).
6. **`/docs` quedará bloqueado al activar el login:** `SecurityConfig` no hace `permitAll()` para `/docs/**` ni `/images/**`.

### Funcionalidad
7. **El reporte `ventas-resumen` cuenta ventas anuladas.** `ReportesService` suma toda venta PAGADA del periodo, incluidas las ANULADAS (agregadas en S09). Revisar también `/reportes/cobranza`.
8. **Al anular una venta** no se cancelan sus mantenimientos programados ni se genera nota de crédito por el comprobante ya emitido.
9. **Producto sin categoría.** `Producto` no tiene relación con `Categoria`, aunque la S3 del curso pide Categoría–Producto.
10. **CRUD de producto incompleto.** Solo existe `GET /productos`; faltan crear, editar y eliminar (S2 del curso). Clientes tampoco tiene editar ni eliminar.
11. **Autocompletado RUC/DNI simulado.** `ConsultaRucClientSimulado` tiene `@Primary`; el comentario de `ConsultaRucClientDecolecta` dice lo contrario. Definir qué proveedor usar.
12. **Correlativo de comprobantes aleatorio** (`Math.random()` en `FacturacionService`). SUNAT exige numeración secuencial por serie.
13. **WhatsApp simulado:** `NotificadorSimulado` solo escribe en el log.
14. **Mantenimientos solo para CLIMATIZACIÓN y REFRIGERACIÓN.** Confirmar con el cliente si VENTILACIÓN FORZADA también los requiere.
15. **Alerta de stock mínimo** sobre todos los productos (`stockActual <= stockMinimo`), incluidos los equipos, que no controlan stock. Confirmar si solo debería aplicar a materiales.
16. **Cotizaciones vencidas:** existe `marcarVencidas()`, pero no está programada (`@Scheduled`).
17. **Moneda mixta:** el backend acepta una venta con productos en soles y en dólares y toma la moneda del último. La SPA ya lo bloquea; falta validarlo en el backend.
18. **`ProductoController` usa el repositorio directamente**, sin pasar por `ProductoService`.

### Arquitectura y alineamiento con el curso
19. **Versión de Spring Boot.** Esta documentación decía 3.5.16; el `pom.xml` usa **3.3.4**. La guía del curso (ADR-003) fija **Spring Boot 4.0.7**. Confirmar con el docente si es obligatorio migrar.
20. **Spring Modulith no está implementado** (ADR-002) ni hay `ModularityTests`. La regla de módulos solo está documentada.
21. **Límites entre módulos incumplidos:** `FacturacionService` usa `VentaRepository`, y `ReportesService` usa `VentaRepository` y `CotizacionRepository`. Se resuelve en parte con la decisión de **reportes por módulo**: mover `ventas-resumen` y `cobranza` a `ventas`, y `cotizaciones-pendientes` a `cotizaciones`.
22. **Esquemas Oracle por módulo.** El curso propone esquemas `BOM_CATALOGO`, `BOM_VENTAS`, etc.; aquí todo está en un solo esquema (`JDREFRIGERACION`), y `CONTEXTO.md` mencionaba un prefijo `JD_<MODULO>` que no se usa.
23. **Paquete raíz.** Documentaciones anteriores decían `com.jdrefrigeracion` o `pe.edu.upeu.sgejd`; el real es `com.bomerp`.
24. **Seguridad:** el curso pide JWT en S10. El HTTP Basic actual es transitorio.
25. **Renombrar el módulo Mantenimiento a Posventa en el código.** El equipo lo llama Posventa, pero el paquete sigue siendo `com.bomerp.mantenimiento` y el endpoint `/mantenimientos`. Decidir si se renombra el paquete (y la ruta) o si se deja documentado así.
26. **Seguridad sí es un módulo.** Spring Security aporta el mecanismo, pero usuarios, roles, permisos y JWT son código propio y el curso lo exige como módulo (S10–S11, esquema `BOM_SEGURIDAD`). Se mantiene como módulo transversal.
27. **Herramienta de modernización:** `.github/modernize/` registra un intento de migrar a Java 25 / Boot 3.5.16 que no se aplicó. Confirmar y borrar esa carpeta si ya no sirve.

### Pruebas y configuración
28. **Pruebas automatizadas casi inexistentes.** `ProductoCrudTest` necesita Oracle encendido, y `HashGenTest` solo imprime un hash. No hay pruebas de ventas, de rollback ni de módulos.
29. **H2 incluido pero sin perfil.** La dependencia está en el `pom.xml`, pero no hay configuración H2 activa. Además, `application-test.properties` está en `src/main/resources` y solo cambia el puerto.
30. **Paquete de arreglos 2 sin aplicar.** `Claude/arreglos2` (pruebas de ventas, correlativo secuencial, límites entre módulos) no está en el código. **No aplicar el `.patch` tal cual:** chocaría con los cambios de la S09 en ventas; portar a mano lo que se quiera.
31. **Contenedor Docker:** la documentación anterior lo llamaba `SGE-DJ`; el real es **`SGE-JD`**. El volumen `sge-jd-oracle-data` está marcado como `external` en `docker-compose.yml`, así que debe existir antes de `docker compose up`.
32. **Requisitos "16 RF consolidados en 8":** la documentación anterior lo decía, pero no existe esa versión consolidada. Confirmar si hay otro documento con los 8 RF.
