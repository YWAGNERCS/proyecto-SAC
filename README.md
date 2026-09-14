# DOCUMENTACIÓN PARA EL SISTEMA WEB "JD REFRIGERACIÓN"

---

## 1. DOCUMENTACIÓN FUNCIONAL (¿Qué hace el sistema?)

### Objetivo general
Desarrollar e implementar un sistema integral web (ERP/CRM) para la empresa JD Refrigeración S.A.C., que permita centralizar y automatizar el control de su catálogo de productos, registro inteligente de clientes/proveedores, flujo completo de cotizaciones y ventas, y la gestión proactiva de mantenimientos técnicos, mejorando así la eficiencia operativa y la atención al cliente.

### Alcance del proyecto
**Incluye:**
- Gestión del catálogo de productos con soporte multimoneda y cálculo de rentabilidad.
- Módulo de Clientes y Proveedores con autocompletado en tiempo real usando APIs de RUC/DNI.
- Módulo de Cotizaciones con exportación instantánea a PDF y envío automático por correo (SMTP).
- Módulo de Ventas con control de créditos (pagos pendientes) y facturación electrónica simulada.
- Módulo de Mantenimientos y Garantías con alertas automáticas (Cron Jobs) al correo de la oficina.
- Módulo de Inventario con incremento automático tras compras y reportes programados de stock mínimo.
- **Módulo de Reportes** con cálculo en tiempo real de ingresos totales y cuentas por cobrar (Soles y Dólares).

**Queda explícitamente fuera del alcance:**
- Módulo de planillas y recursos humanos.
- Pasarelas de pago online con tarjeta de crédito (las ventas se registran como pagadas o a crédito internamente).
- Control de GPS de los técnicos de mantenimiento.

### Módulos Desarrollados
1. **Módulo de Catálogo:** Gestión de productos, cálculo de precios y stock.
2. **Módulo de Clientes:** Registro de clientes B2B/B2C con autocompletado RUC/DNI.
3. **Módulo de Proveedores y Compras:** Gestión del reabastecimiento de inventario.
4. **Módulo de Cotizaciones:** Emisión rápida de presupuestos formales (PDF) enviados por correo.
5. **Módulo de Ventas:** Conversión de cotizaciones a ventas sin redigitar, control de ventas a crédito y al contado.
6. **Módulo de Facturación:** Emisión de comprobantes de pago electrónicos (UBL 2.1 simulado).
7. **Módulo de Inventario:** Control estricto de entradas y salidas de stock, con alertas por bajo inventario.
8. **Módulo de Mantenimientos:** Generación de cronogramas para los mantenimientos preventivos a futuro.
9. **Módulo de Notificaciones:** Motor de envíos asíncronos por Correo Electrónico (SMTP) y WhatsApp.
10. **Módulo de Reportes:** Panel analítico de ingresos brutos y cuentas por cobrar en múltiples monedas.

### Requerimientos Funcionales (RF)
- **RF-01 (Gestión de Accesos):** El sistema debe proveer un mecanismo de autenticación y autorización basado en roles (Administrador, Asistente) para restringir el acceso a los módulos operativos.
- **RF-02 (Integración Externa):** El sistema debe integrar servicios de terceros (API REST `apis.net.pe`) para la resolución automática de datos fiscales (Razón Social y Dirección) a partir de un RUC/DNI válido.
- **RF-03 (Emisión Documental):** El sistema debe generar automáticamente documentos pre-formateados en formato PDF para las cotizaciones, incrustando la identidad visual corporativa y variables dinámicas.
- **RF-04 (Flujo Transaccional):** El sistema debe habilitar la conversión directa de una entidad "Cotización" (en estado Aceptada) hacia una entidad "Venta", garantizando la persistencia de datos sin redundancia de ingreso manual.
- **RF-05 (Control de Kardex):** El sistema debe gestionar el inventario mediante actualizaciones atómicas; decrementando el stock al procesar una venta y aplicando un incremento tras el registro de compras.
- **RF-06 (Planificación de Servicios):** El sistema debe programar automáticamente ciclos de mantenimiento preventivo (4 periodos) tras detectar la facturación de equipos categorizados como "Climatización" o "Refrigeración".
- **RF-07 (Procesamiento Asíncrono):** El sistema debe ejecutar procesos en segundo plano (Cron Jobs) con periodicidad diaria a las 08:00 AM, evaluando umbrales de stock crítico e inminencia de mantenimientos (ventana de 7 días) para despachar alertas vía SMTP.
- **RF-08 (Analítica Financiera):** El sistema debe calcular y consolidar en tiempo real las métricas de ingresos brutos y cuentas por cobrar, aplicando segmentación por divisa transaccional (Soles y Dólares).

### Requerimientos No Funcionales (RNF)
- **RNF-01 (Arquitectura de Software):** El sistema debe adherirse al patrón arquitectónico Modelo-Vista-Controlador (MVC), estructurado lógicamente en 3 capas (Controladores, Servicios, Repositorios) para garantizar alta cohesión y bajo acoplamiento.
- **RNF-02 (Rendimiento y Latencia):** El tiempo de procesamiento y respuesta de los endpoints analíticos y transaccionales no debe superar los 1000 milisegundos bajo carga de trabajo estándar.
- **RNF-03 (Interoperabilidad):** El backend debe operar de forma completamente desacoplada de la interfaz gráfica, exponiendo estrictamente APIs RESTful bajo el estándar de mensajería JSON.
- **RNF-04 (Mantenibilidad e IoC):** El código fuente debe implementar el patrón de Inyección de Dependencias gestionado por Spring Framework, debiendo externalizar todas las credenciales y variables sensibles en archivos de configuración (`properties`).
- **RNF-05 (Seguridad de la Información):** Todos los endpoints administrativos (`/api/admin/**`) deben aplicar políticas de interceptación de seguridad (HTTP Basic Auth). Las contraseñas en base de datos deben aplicar funciones de hashing criptográfico unidireccional (BCrypt).

### Casos de Uso / Historias de Usuario
- **Caso de Uso 1 (Cotizar):** El actor "Vendedor" ingresa al sistema, selecciona el cliente, escoge productos del catálogo. El sistema calcula IGV y totales. El vendedor guarda, y el sistema envía el PDF al cliente.
- **Caso de Uso 2 (Alertas de Garantía):** El "Sistema (Actor Autónomo)" despierta a las 8:00 AM, consulta la base de datos por mantenimientos pendientes a 7 días. Agrupa los resultados, arma un documento HTML y lo envía a la oficina para que llamen al cliente.

---

## 2. DOCUMENTACIÓN TÉCNICA (¿Cómo está construido?)

### Stack Tecnológico
- **Backend:** Java 21 (JDK 21)
- **Framework Principal:** Spring Boot 3.3.4
- **ORM / Persistencia:** Spring Data JPA / Hibernate
- **Seguridad:** Spring Security (HTTP Basic Auth)
- **Base de Datos:** Oracle Database Express Edition (21c/11g)
- **Librerías Extra:** `openpdf` (Para generar reportes/PDFs), `spring-boot-starter-mail` (SMTP JavaMail), `springdoc-openapi` (Swagger para documentación).

### Arquitectura de Software
Se ha utilizado un **Patrón de Arquitectura en Capas** con filosofía **MVC (Modelo-Vista-Controlador)** para las APIs REST:
1. **Capa de Controladores (Controllers):** Expone los endpoints REST (`/api/admin/...`) y gestiona las solicitudes HTTP.
2. **Capa de Servicio (Services):** Contiene las reglas de negocio (ej. calcular subtotales, verificar estados antes de procesar ventas).
3. **Capa de Repositorio (Repositories):** Interfaces JPA que se comunican directamente con Oracle sin necesidad de escribir SQL manual.
4. **Capa de Integración:** Clientes Rest (`ConsultaRucClient`, `NubefactClientSimulado`) para comunicarse con APIs externas.

### Modelo y Diccionario de Datos
*El sistema está completamente normalizado (3FN).*
- **Tabla `PRODUCTOS`:** Almacena `id` (PK), `nombre`, `sector`, `costo_base`, `moneda`, `stock_actual`, `stock_minimo`.
- **Tabla `CLIENTES` / `PROVEEDORES`:** Almacena `id` (PK), `tipo` (Empresa o Natural), `ruc`, `dni`, `correo`.
- **Tabla `VENTAS`:** Almacena la cabecera transaccional. `id` (PK), `cliente_id` (FK), `cotizacion_id` (FK), `tipo_pago`, `estado_pago`.
- **Tabla `DETALLE_VENTAS`:** Desglosa la venta. `id` (PK), `venta_id` (FK), `producto_id` (FK), `cantidad`, `precio_unitario`.
- **Tabla `MANTENIMIENTOS`:** `id` (PK), `venta_id` (FK), `fecha_programada`, `estado`, `alerta_enviada`.

### Estructura del Código (Directorio Principal)
```text
src/main/java/com/jdrefrigeracion/
├── catalogo/        (Lógica de productos)
├── clientes/        (Lógica de clientes y API RUC)
├── compras/         (Lógica de proveedores e ingreso de mercadería)
├── config/          (Configuración de Spring Security)
├── cotizaciones/    (Generación de PDF e Items de cotización)
├── facturacion/     (Emisión de boletas/facturas electrónicas OSE)
├── inventario/      (Reportes y alertas de stock mínimo)
├── mantenimiento/   (Cron Jobs diarios y alertas de garantías)
├── notificaciones/  (Gestor de envíos SMTP y WhatsApp)
├── reportes/        (Cálculos matemáticos de ingresos y deudas)
└── ventas/          (Procesamiento de ventas y créditos)
```

### Guía de Instalación y Despliegue
1. Instalar **Java JDK 21** y configurar variables de entorno `JAVA_HOME`.
2. Instalar **Oracle Database XE** (Asegurar que escuche en `localhost:1521/xe`).
3. En el archivo `application.properties`, colocar las credenciales de Oracle:
   ```properties
   spring.datasource.url=jdbc:oracle:thin:@localhost:1521:xe
   spring.datasource.username=SYSTEM
   spring.datasource.password=Oracle123
   ```
4. Abrir la terminal en la raíz del proyecto y ejecutar la instalación de dependencias:
   `mvn clean install`
5. Ejecutar el servidor embebido (Tomcat) de Spring Boot:
   `mvn spring-boot:run`

---

## 3. MANUAL DE USUARIO (¿Cómo se opera?)

### Requisitos de Entorno
- **Navegador Web:** Google Chrome, Mozilla Firefox o Microsoft Edge actualizados.
- **Conexión a Internet:** Requerido obligatoriamente para emitir comprobantes a SUNAT/Nubefact, para consultar RUCs, y enviar PDFs por correo electrónico.

### Guía de Inicio Rápido
1. El sistema inicia en segundo plano protegiendo las rutas administrativas.
2. Todo acceso al sistema requerirá iniciar sesión.
   - Cuenta de Gerencia: `lisandro` / `123456`
   - Cuenta de Asistente: `lucy` / `654321`

### Flujos de Operación (Ejemplos Críticos)
- **Para registrar un cliente B2B (Empresa):** Ingresar a la sección Clientes, colocar únicamente el RUC (ej. 20100055237) y hacer clic en autocompletar. El sistema automáticamente traerá el nombre y dirección para guardarlo.
- **Para generar una Venta desde una Cotización:** Buscar la cotización por su ID. Verificar que su estado sea "Aceptada". Seleccionar si es al contado o al crédito (indicando los días de crédito) y presionar "Convertir". El sistema descontará el stock y emitirá la Factura.
- **Para extraer Reportes Financieros:** Ingresar al panel administrativo y consultar los endpoints de `/api/admin/reportes`. El sistema sumará automáticamente el dinero de las ventas y las deudas pendientes discriminando por moneda (Soles o Dólares).
- **Para revisar mantenimientos:** No es necesario buscar los mantenimientos manualmente. El sistema informará a la asistente mediante un correo a las 8:00 AM exactas indicándole a quién debe llamar en la semana.

### Mantenimiento y FAQ
- **Pregunta:** *"Generé la cotización pero el cliente no recibió el correo."*
  - **Solución:** Verifique si el correo del cliente no terminó en `.con` en vez de `.com`. Reenvíe la cotización forzando el botón de notificar. Asegúrese de que el servidor tenga acceso al puerto 587 (SMTP de Gmail).
- **Pregunta:** *"El sistema dice que no puede procesar la Venta porque el stock está en negativo."*
  - **Solución:** La política de la empresa sí permite stock en negativo. Si este error ocurre, notifique al equipo de soporte, probablemente el producto esté inhabilitado en catálogo.
- **Soporte Técnico:** En caso de errores 500 del servidor, contactar al proveedor del desarrollo (Equipo Estudiantil).

---

## 4. ESTADO Y TRAZABILIDAD DEL PROYECTO (Fases de Ingeniería de Software)

Este documento organiza el proyecto en las 13 fases estándar de un Sistema de Gestión Empresarial, adaptadas a lo que ya se ha construido y lo que falta. Sirve como índice maestro: para cada fase se indica qué artefacto la representa y su estado actual.

### 1. Inicio y planificación
**Qué se documenta:** Problema, objetivos, alcance y viabilidad.
**Entregables:**
- Brief del Proyecto (contexto, objetivo, alcance, stakeholders, cronograma)
- Planteamiento del problema y propuesta de solución
**Estado:** ✅ Completo

### 2. Levantamiento de información
**Qué se documenta:** Cómo trabaja actualmente la empresa.
**Entregables:**
- Transcripciones de las 2 reuniones con Lisandro Madrid Laos
- Contexto consolidado: manejo actual en Excel, dolor principal (doble digitación cotización-SUNAT)
**Estado:** ✅ Completo - pendiente confirmar formato de envío a SUNAT

### 3. Análisis de requisitos
**Qué se documenta:** Qué debe hacer el sistema.
**Entregables:**
- 16 Requerimientos Funcionales y 9 Requerimientos No Funcionales
- Reglas de negocio clave (margen, multimoneda, IGV, alertas)
**Estado:** ✅ Completo

### 4. Modelado de procesos
**Qué se documenta:** Cómo funcionan los procesos empresariales (AS-IS y TO-BE).
**Entregables:**
- AS-IS: cotización en Excel -> manual a SUNAT
- TO-BE: cotización en sistema -> conversión a venta -> facturación OSE
**Estado:** ⏳ Pendiente formalizar como diagrama BPMN

### 5. Análisis del sistema
**Qué se documenta:** Relación entre usuarios, procesos y funcionalidades.
**Entregables:**
- Actores: Lisandro (admin), Lucy, Cliente
- Casos de uso implícitos (Cotizar, Vender, Comprar, Facturar)
**Estado:** ⏳ Pendiente formalizar diagrama de Casos de Uso UML

### 6. Diseño del sistema
**Qué se documenta:** Cómo se construirá técnicamente.
**Entregables:**
- Vistas arquitectónicas C1, C2, C3, C4
- Arquitectura monolítica modular (Spring Boot + Oracle)
- Principios SOLID y Diseño de Base de Datos
**Estado:** ✅ Completo - pendiente diagrama de clases UML formal

### 7. Desarrollo / implementación
**Qué se documenta:** Construcción del software.
**Entregables (Módulos):**
- Catálogo: ✅
- Clientes (RUC/DNI): ✅
- Cotizaciones (+PDF): ✅
- Ventas (+descuento stock): ✅
- Compras (+incremento stock): ✅
- Proveedores: ✅
- Inventario (alertas): ✅
- Facturación: 🔄 Backend completo (simulado), falta producción Nubefact
- Mantenimiento (Cron): ✅
- Administración (Login): ✅
- Reportes: ✅
- Frontend: ❌ No construido
**Estado:** 🔄 En progreso avanzado (11 de 12 módulos backend completos)

### 8. Pruebas y validación
**Qué se documenta:** Comprobar que cumple los requisitos.
**Entregables:**
- Verificación de compilación (javac)
- Pruebas de lógica de negocio (margen, IGV, conversión cotización, cobranzas)
- Hallazgos corregidos (equals, adjuntos, endpoints)
**Estado:** 🔄 Pruebas unitarias informales realizadas; falta plan formal (PT-001)

### 9. Implementación / despliegue
**Qué se documenta:** Puesta en funcionamiento.
**Entregables:**
- DB local configurada (Oracle/H2). Pendiente hosting nube.
**Estado:** ⏳ Pendiente despliegue a cliente

### 10. Documentación técnica
**Qué se documenta:** Info para desarrolladores.
**Entregables:**
- Este archivo README y comentarios en código
**Estado:** 🔄 Parcial - falta manual técnico consolidado

### 11. Manual de usuario
**Qué se documenta:** Cómo utilizar el sistema visualmente.
**Estado:** ⏳ Pendiente (depende del Frontend)

### 12. Trazabilidad
**Qué se documenta:** Relación entre lo solicitado y lo implementado.
**Estado:** ⏳ Pendiente formalizar como matriz completa (RTM). Entregable crítico para la siguiente unidad.

### 13. Mantenimiento y evolución
**Qué se documenta:** Control de cambios.
**Estado:** ⏳ Pendiente formalizar control de versiones real (Git).

---

### Resumen visual del avance

| Fase | Título | Estado |
|---|---|---|
| 1 | Inicio y planificación | ✅ Completo |
| 2 | Levantamiento de información | ✅ Completo |
| 3 | Análisis de requisitos | ✅ Completo |
| 4 | Modelado de procesos (BPMN) | ⏳ Pendiente |
| 5 | Análisis del sistema (UML) | ⏳ Pendiente |
| 6 | Diseño del sistema | ✅ Completo |
| 7 | Desarrollo | 🔄 Avanzado |
| 8 | Pruebas y validación | 🔄 Parcial |
| 9 | Implementación/despliegue | ⏳ Pendiente |
| 10 | Documentación técnica | 🔄 Parcial |
| 11 | Manual de usuario | ⏳ Pendiente |
| 12 | Trazabilidad (RTM) | ⏳ Pendiente |
| 13 | Mantenimiento y evolución | ⏳ Pendiente |

> **Prioridad sugerida:** Cerrar el módulo de Frontend (Fase 7) y construir la Matriz de Trazabilidad (Fase 12), fundamentales para la evaluación del curso.
