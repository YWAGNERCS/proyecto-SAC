# DOCUMENTACIÓN DEL SISTEMA WEB "JD REFRIGERACIÓN"

> ⚠️ **Alerta de seguridad activa** — `application.properties` contiene una contraseña real de aplicación de Gmail en texto plano (`spring.mail.password`). Debe revocarse en la cuenta de Google y reemplazarse por una variable de entorno antes de compartir este repositorio con nadie más o subirlo a GitHub. Este aviso se mantiene aquí hasta confirmar que fue resuelto.

---

## Índice
1. [Documentación funcional](#1-documentación-funcional-qué-hace-el-sistema)
2. [Documentación técnica](#2-documentación-técnica-cómo-está-construido)
3. [Manual de usuario](#3-manual-de-usuario-cómo-se-opera)
4. [Estado y trazabilidad del proyecto](#4-estado-y-trazabilidad-del-proyecto-fases-de-ingeniería-de-software)

---

## 1. DOCUMENTACIÓN FUNCIONAL (¿Qué hace el sistema?)

### Objetivo general
Desarrollar e implementar un sistema integral web (ERP/CRM) para JD Refrigeración S.A.C., que centralice y automatice el catálogo de productos, el registro inteligente de clientes/proveedores, el flujo completo de cotizaciones y ventas, y la gestión proactiva de mantenimientos técnicos, mejorando la eficiencia operativa y la atención al cliente.

### Alcance del proyecto

**Incluye:**
- Catálogo de productos con soporte multimoneda (soles/dólares) y cálculo de rentabilidad por margen porcentual.
- Clientes y Proveedores con autocompletado en tiempo real vía API de RUC/DNI.
- Cotizaciones con exportación a PDF y envío automático por correo (SMTP).
- Ventas con control de crédito/contado y conversión directa desde cotización aceptada.
- Facturación electrónica (simulada, a la espera de credenciales productivas de un OSE).
- Mantenimientos y Garantías con alertas automáticas (Cron Job diario).
- Inventario con incremento automático tras compras y alertas de stock mínimo.
- Reportes: ventas por periodo, cobranza (separando pendientes de atrasadas), y cotizaciones pendientes.

**Queda explícitamente fuera del alcance:**
- Módulo de planillas y recursos humanos.
- Pasarelas de pago online con tarjeta de crédito (las ventas se registran como pagadas o a crédito, sin procesar pagos reales).
- Control de GPS de los técnicos de mantenimiento.
- Frontend del portal público (**pendiente de construir** — hoy solo existe como API REST, sin interfaz visual).

### Módulos desarrollados (backend)

| # | Módulo | Estado |
|---|---|---|
| 1 | Catálogo | ✅ |
| 2 | Clientes (autocompletado RUC/DNI) | ✅ |
| 3 | Proveedores y Compras | ✅ |
| 4 | Cotizaciones (+ PDF, + envío por correo) | ✅ |
| 5 | Ventas (+ conversión desde cotización, + control de crédito) | ✅ |
| 6 | Facturación (Comprobante, XML UBL 2.1) | ⚠️ Simulado — falta integración productiva con OSE (Nubefact) |
| 7 | Inventario (alertas de stock mínimo) | ✅ |
| 8 | Mantenimientos (cronogramas preventivos automáticos) | ✅ |
| 9 | Notificaciones (SMTP + WhatsApp) | ✅ |
| 10 | Reportes (ventas por periodo, cobranza, cotizaciones pendientes) | ✅ |
| 11 | Administración (login y roles) | ✅ |
| — | **Frontend del portal público** | ❌ No construido |

### Requerimientos Funcionales (RF)

> Nota: A continuación se detallan los **16 Requerimientos Funcionales (RF)** originales del proyecto.

- **RF-01 (Autenticación):** El sistema debe permitir el inicio de sesión con credenciales seguras.
- **RF-02 (Roles y Permisos):** El sistema debe restringir módulos según el rol del usuario (Administrador, Asistente).
- **RF-03 (Catálogo de Productos):** El sistema debe gestionar productos soportando moneda dual (soles/dólares) y calcular rentabilidad según margen porcentual.
- **RF-04 (Autocompletado de Clientes/Proveedores):** El sistema debe consultar una API externa (apis.net.pe) para extraer Razón Social y Dirección a partir del RUC o DNI.
- **RF-05 (Gestión de Cotizaciones):** El sistema debe permitir la creación de cotizaciones calculando IGV y subtotales automáticamente.
- **RF-06 (Exportación a PDF):** El sistema debe generar un documento PDF formal y con diseño corporativo al guardar una cotización.
- **RF-07 (Envío de Cotizaciones por Correo):** El sistema debe despachar la cotización en formato PDF directamente al correo del cliente vía SMTP.
- **RF-08 (Conversión a Venta):** El sistema debe permitir convertir una Cotización (en estado Aceptada) en una Venta sin tener que redigitar los datos.
- **RF-09 (Control de Créditos):** El sistema debe registrar si una venta es al contado o al crédito, llevando un control de deudas pendientes.
- **RF-10 (Facturación Electrónica):** El sistema debe generar la estructura para la emisión de comprobantes (simulación UBL 2.1).
- **RF-11 (Actualización de Stock - Salidas):** El sistema debe descontar atómicamente el inventario al procesar una venta.
- **RF-12 (Actualización de Stock - Entradas):** El sistema debe incrementar el inventario automáticamente al registrar compras a proveedores.
- **RF-13 (Alertas de Stock Mínimo):** El sistema debe evaluar diariamente los productos bajo el stock crítico y generar alertas.
- **RF-14 (Programación de Mantenimientos):** El sistema debe programar automáticamente 4 mantenimientos preventivos tras vender equipos de refrigeración/climatización.
- **RF-15 (Alertas de Mantenimiento Asíncronas):** Un proceso en segundo plano (Cron Job) debe buscar a las 8:00 AM los mantenimientos próximos a 7 días y enviar un correo a la oficina.
- **RF-16 (Reportes y Analítica Financiera):** El sistema debe consolidar en tiempo real los ingresos y las cuentas por cobrar, aplicando segmentación por moneda.

### Requerimientos No Funcionales (RNF)

- **RNF-01 (Arquitectura):** Patrón en capas (Controladores, Servicios, Repositorios) sobre un monolito modular, con alta cohesión y bajo acoplamiento.
- **RNF-02 (Rendimiento):** Tiempo de respuesta de endpoints transaccionales y analíticos no debe superar 1000 ms bajo carga estándar. *(pendiente de medición formal — hoy es un objetivo, no un dato verificado)*
- **RNF-03 (Interoperabilidad):** Backend desacoplado del frontend, exponiendo únicamente APIs RESTful en JSON.
- **RNF-04 (Mantenibilidad e IoC):** Inyección de dependencias vía Spring; **toda credencial sensible debe externalizarse** en variables de entorno (ver alerta de seguridad al inicio de este documento — actualmente incumplido en `application.properties`).
- **RNF-05 (Seguridad):** Endpoints `/api/admin/**` protegidos con HTTP Basic Auth; contraseñas de usuario almacenadas con BCrypt.

### Casos de uso / Historias de usuario

- **CU-01 (Cotizar):** El Vendedor selecciona cliente y productos; el sistema calcula IGV y totales; al guardar, envía el PDF al cliente automáticamente.
- **CU-02 (Alertas de garantía):** El sistema, de forma autónoma, revisa diariamente a las 8:00 AM los mantenimientos próximos a 7 días y envía un correo a la oficina con la lista de clientes a contactar.

---

## 2. DOCUMENTACIÓN TÉCNICA (¿Cómo está construido?)

### Stack tecnológico

- **Backend:** Java 21 (JDK 21) — *ver nota de versión más abajo*
- **Framework:** Spring Boot 3.5.16
- **ORM/Persistencia:** Spring Data JPA / Hibernate
- **Seguridad:** Spring Security (HTTP Basic Auth + BCrypt)
- **Base de datos:** Oracle Database Express Edition (desarrollo local también soporta H2 en memoria)
- **Librerías adicionales:** `openpdf` 1.3.32 (PDFs), `spring-boot-starter-mail` (SMTP), `springdoc-openapi-starter-webmvc-ui` 2.6.0 (Swagger)

> **Nota sobre la versión de Java:** existe un registro de una herramienta automatizada de modernización que intentó actualizar el proyecto a Java 25 (con Spring Boot 3.5.16) y reportó éxito en compilación y pruebas. Sin embargo, el `pom.xml` actual todavía declara `<java.version>21</java.version>`. **Antes de asumir que el proyecto corre en Java 25, hay que confirmar con quien ejecutó esa herramienta si el cambio se revirtió intencionalmente o quedó a medias.**

### Arquitectura de software

Monolito modular con patrón en capas (estilo MVC para las APIs REST):

1. **Controllers** — exponen los endpoints REST (`/api/admin/...`, `/api/publico/...`).
2. **Services** — contienen las reglas de negocio (cálculo de subtotales, validaciones de estado, etc.).
3. **Repositories** — interfaces JPA que se comunican con Oracle/H2 sin SQL manual.
4. **Integrations** — clientes REST hacia servicios externos (`ConsultaRucClient`, `OseClient`/`NubefactClientSimulado`).

### Modelo de datos (resumen)

| Tabla | Campos clave |
|---|---|
| `PRODUCTOS` | id (PK), nombre, sector, costo_base, moneda, stock_actual, stock_minimo |
| `CLIENTES` / `PROVEEDORES` | id (PK), tipo (Empresa/Natural), ruc, dni, correo |
| `VENTAS` | id (PK), cliente_id (FK), cotizacion_id (FK), tipo_pago, estado_pago |
| `DETALLE_VENTAS` | id (PK), venta_id (FK), producto_id (FK), cantidad, precio_unitario |
| `MANTENIMIENTOS` | id (PK), venta_id (FK), fecha_programada, estado, alerta_enviada |

*El modelo apunta a 3FN; pendiente de verificación formal con diagrama entidad-relación completo.*

### Estructura del código

```text
src/main/java/com/jdrefrigeracion/
├── catalogo/        (productos, precios, stock)
├── clientes/         (clientes + integración RUC/DNI)
├── compras/          (registro de compras, incremento de stock)
├── proveedores/      (datos maestros de proveedores)
├── config/           (Spring Security)
├── cotizaciones/     (cotizaciones, generación de PDF)
├── facturacion/      (comprobantes electrónicos, integración OSE)
├── inventario/       (alertas de stock mínimo)
├── mantenimiento/    (cron jobs, alertas de garantía)
├── notificaciones/   (envío por SMTP y WhatsApp)
├── reportes/         (ventas por periodo, cobranza, cotizaciones pendientes)
├── seguridad/        (usuarios, roles, autenticación)
└── ventas/           (procesamiento de ventas y créditos)
```

### Guía de instalación

1. Instalar **JDK 21** (confirmar que coincide con lo que declara `pom.xml` antes de instalar otra versión) y configurar `JAVA_HOME`.
2. Instalar **Oracle Database XE** (o usar el perfil H2 en memoria ya configurado para desarrollo).
3. Configurar credenciales de base de datos **como variables de entorno**, no directamente en `application.properties`:
   ```properties
   spring.datasource.url=jdbc:oracle:thin:@localhost:1521:xe
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   spring.mail.password=${MAIL_PASSWORD}
   ```
4. Ejecutar `mvn clean install`.
5. Ejecutar `mvn spring-boot:run`.

> **Control de versiones:** el workspace no está inicializado como repositorio Git (confirmado también por la herramienta de modernización automática). Se recomienda crear un repositorio compartido (GitHub) cuanto antes — ya se ha perdido trabajo entre integrantes por depender de compartir ZIPs manualmente.

---

## 3. MANUAL DE USUARIO (¿Cómo se opera?)

### Requisitos de entorno
- Navegador web actualizado (Chrome, Firefox o Edge).
- Conexión a internet obligatoria (consulta de RUC/DNI, envío de correos, facturación electrónica).

### Acceso al sistema

> ⚠️ Las credenciales de ejemplo de esta sección son solo para el entorno de desarrollo/pruebas. **No deben quedar documentadas en texto plano en un repositorio una vez el sistema pase a un ambiente real** — reemplazar por contraseñas generadas y gestionadas de forma segura.

- Cuenta de Gerencia: `lisandro`
- Cuenta de Asistente: `lucy`

### Flujos de operación

- **Registrar cliente B2B:** ingresar el RUC y usar el autocompletado; el sistema trae razón social y dirección automáticamente.
- **Convertir cotización en venta:** buscar la cotización por ID, confirmar que esté "Aceptada", elegir contado/crédito y presionar "Convertir" — el sistema descuenta stock y genera el comprobante.
- **Consultar reportes financieros:** `/api/admin/reportes/ventas-resumen` (acepta `?desde=&hasta=`), `/api/admin/reportes/cobranza` (separa pendientes de atrasadas), `/api/admin/reportes/cotizaciones-pendientes`.
- **Mantenimientos:** no requiere revisión manual — el sistema envía un correo diario a las 8:00 AM con los clientes a contactar en los próximos 7 días.

### Preguntas frecuentes

- **"El cliente no recibió el correo de la cotización":** verificar el dominio del correo (errores comunes como `.con` en vez de `.com`); confirmar que el servidor tenga salida al puerto 587 (SMTP).
- **"El sistema permite stock negativo":** es una decisión de política de negocio, no un error — si ocurre de forma inesperada, revisar si el producto está mal configurado en el catálogo.
- **Soporte técnico:** equipo de desarrollo estudiantil (Yoel Wagner, Aron Rodrigo, Ronald Apaza).

---

## 4. ESTADO Y TRAZABILIDAD DEL PROYECTO (Fases de Ingeniería de Software)

| # | Fase | Estado |
|---|---|---|
| 1 | Inicio y planificación | ✅ Completo |
| 2 | Levantamiento de información | ✅ Completo (pendiente confirmar formato exacto de envío a SUNAT con Lucy) |
| 3 | Análisis de requisitos | ✅ Completo (16 RF originales, consolidados en 8 en esta versión) |
| 4 | Modelado de procesos (BPMN AS-IS/TO-BE) | ⏳ Pendiente |
| 5 | Análisis del sistema (casos de uso UML) | ⏳ Pendiente formalizar diagrama |
| 6 | Diseño del sistema (C1–C4, SOLID, BD) | ✅ Completo (pendiente diagrama de clases UML formal) |
| 7 | Desarrollo | 🔄 11 de 12 módulos backend completos; frontend no iniciado |
| 8 | Pruebas y validación | 🔄 Pruebas de lógica realizadas de forma informal; falta plan de pruebas formal (PT-001, PT-002...) |
| 9 | Implementación/despliegue | ⏳ Pendiente — sin ambiente accesible para el cliente aún |
| 10 | Documentación técnica | 🔄 Este documento; falta manual técnico consolidado independiente |
| 11 | Manual de usuario | ⏳ Pendiente (depende del frontend) |
| 12 | Trazabilidad (RTM) | ⏳ **Pendiente — siguiente entregable prioritario** |
| 13 | Mantenimiento y evolución | ⏳ Pendiente control de versiones real (Git) |

> **Prioridad sugerida:** (1) resolver la alerta de seguridad de la contraseña expuesta, (2) construir la Matriz de Trazabilidad de Requisitos (Fase 12) mapeando los 16 RF originales → módulo → clase → prueba, (3) iniciar el Frontend del portal público.
