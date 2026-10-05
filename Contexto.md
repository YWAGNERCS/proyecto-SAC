# SGE-JD — Sistema de Gestión Empresarial para JD Refrigeración S.A.C.

**Tipo de proyecto:** Backend empresarial modular (Spring Boot + Oracle)
**Cliente:** JD Refrigeración S.A.C. (Lisandro Javier Madrid Laos, propietario)
**Curso:** Proyecto Integrador — ADS / BD2 / LP2, Universidad Peruana Unión

---

## 1. Resumen ejecutivo

JD Refrigeración S.A.C. es una empresa con 20 años de operación en climatización, refrigeración y ventilación forzada. Hoy gestiona todo su negocio — cotizaciones, ventas, compras y facturación — en hojas de cálculo desconectadas entre sí, sin ningún sistema que las integre.

Este proyecto construye el backend de un sistema de gestión que centraliza esa operación: un único backend Spring Boot, modular, conectado a Oracle, que expone una API REST consumida por un portal público (catálogo) y un panel administrativo (gestión interna).

No es un ERP genérico. Es una base académica con alcance acotado al flujo comercial real del cliente, extensible, siguiendo el mismo estándar de arquitectura modular verificada que exige el curso (un solo ejecutable, módulos delimitados por paquete, sin microservicios).

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

Monolito modular — **un solo proyecto Maven, un solo ejecutable Spring Boot**, organizado en paquetes de módulo con límites verificados, sobre una única base Oracle con esquemas funcionales por módulo. Sin reactor multi-módulo, sin microservicios, sin comunicación HTTP interna: los módulos se comunican únicamente a través de servicios Java públicos.

```
sge-jd-backend/                    # proyecto Maven único
└── src/main/java/pe/edu/upeu/sgejd/
    ├── SgeJdApplication.java      # único Spring Boot ejecutable
    ├── catalogo/                  # productos, categorías, precios
    ├── clientes/                  # datos maestros + autocompletado RUC/DNI
    ├── proveedores/               # datos maestros de proveedores
    ├── cotizaciones/              # cabecera-detalle: Cotizacion-ItemCotizacion
    ├── ventas/                    # cabecera-detalle: Venta-DetalleVenta
    ├── compras/                   # cabecera-detalle: Compra-DetalleCompra
    ├── inventario/                # control de stock y alertas
    ├── facturacion/               # comprobantes electrónicos (OSE/SUNAT)
    ├── mantenimiento/             # postventa, alertas automáticas
    ├── notificaciones/            # envío WhatsApp/correo (interfaces segregadas)
    ├── reportes/                  # consultas agregadas, sin lógica de negocio propia
    └── seguridad/                 # usuarios, roles, autenticación
```

**Reglas de módulo (iguales a las que exige el curso):**
- Cada paquete administra sus propios repositorios y entidades; ningún módulo accede directo al repositorio de otro.
- Un solo `DataSource`, pero cada módulo es dueño de su porción del esquema (convención `JD_<MODULO>` en los nombres de tabla cuando aplica).
- La comunicación entre módulos es por servicios Java inyectados (`@Service`), nunca por llamadas HTTP internas ni Feign.
- El panel administrativo requiere autenticación (`/api/admin/**`); el catálogo público no (`/api/publico/**`).

---

## 4. Módulos — qué hace cada uno y por qué existe

Para cumplir con la arquitectura y la lógica de negocio de JD Refrigeración S.A.C., los 12 módulos de dominio se agrupan en 4 categorías o tipos principales:

1. **Módulos Maestros (Catálogos base):** Guardan la información central que alimenta al resto del sistema.
2. **Módulos Transaccionales (Cabecera-Detalle):** Manejan las operaciones comerciales atómicas (El Core).
3. **Módulos Operativos y de Soporte:** Aplican reglas de negocio posteriores a las ventas o transversales al stock.
4. **Módulos de Integración y Transversales:** Conectan el sistema con el mundo exterior o protegen la aplicación.

| Módulo | Tipo | Responsabilidad | Entidad(es) cabecera-detalle |
|---|---|---|---|
| **catalogo** | Maestro | Productos/servicios, categorías, precio con margen % sobre costo, soporte multimoneda | `Producto` |
| **clientes** | Maestro | Datos maestros de clientes, autocompletado por RUC/DNI vía API externa | `Cliente` |
| **proveedores** | Maestro | Datos maestros de proveedores | `Proveedor` |
| **cotizaciones** | Transaccional | Elaboración y seguimiento de cotizaciones, generación de PDF, envío por WhatsApp/correo | `Cotizacion` – `ItemCotizacion` |
| **ventas** | Transaccional | Registro de ventas; conversión directa de una cotización aceptada en venta, sin doble digitación | `Venta` – `DetalleVenta` |
| **compras** | Transaccional | Registro de compras a proveedor, con incremento automático de stock | `Compra` – `DetalleCompra` |
| **inventario** | Soporte | Alertas de stock mínimo sobre materiales de servicio | — (transversal a `catalogo`) |
| **facturacion** | Integración | Generación de comprobante electrónico (XML UBL 2.1) e integración con un OSE | `Comprobante` |
| **mantenimiento** | Operativo | Programación automática de mantenimiento preventivo (3/6/9/12 meses) tras la venta de equipos | `Mantenimiento` |
| **notificaciones** | Integración | Envío de correos/WhatsApp; interfaces segregadas por canal (`NotificadorCorreo`, `NotificadorWhatsApp`) | — |
| **reportes** | Soporte | Ventas por periodo, cobranza (pendiente vs. atrasada), cotizaciones pendientes | — (solo lectura agregada) |
| **seguridad** | Transversal | Usuarios, roles (`ADMINISTRADOR`, `ASISTENTE`), autenticación HTTP Basic + BCrypt | `Usuario` |

**Nota de alineamiento con el estándar del curso:** el corte base exigido es `catalogo` + `ventas` funcionales, con `inventario`, `compras` y `seguridad` delimitados arquitectónicamente. Este proyecto va más allá de ese mínimo (cotizaciones, facturación, mantenimiento y reportes ya están funcionales), porque son requerimientos reales confirmados por el cliente, no extensiones especulativas.

---

## 5. Decisiones de arquitectura relevantes

- **Monolito modular, no microservicios**: 2 usuarios administrativos, una sola sede, volumen bajo de transacciones. Microservicios resolverían un problema de escala que este negocio no tiene; solo añadirían costo operativo.
- **DTOs de salida separados de las entidades JPA**: el catálogo público nunca expone costo base ni margen interno, solo el precio final calculado.
- **Interfaces segregadas para integraciones externas** (`ConsultaRucClient`, `OseClient`, `NotificadorCorreo`/`NotificadorWhatsApp`): permite cambiar de proveedor (API de RUC, OSE de facturación) sin tocar la lógica de negocio que depende de ellas.
- **Cabecera-detalle como patrón transversal**: `Cotizacion/ItemCotizacion`, `Venta/DetalleVenta`, `Compra/DetalleCompra` siguen la misma forma, con atomicidad (commit/rollback) en cada operación.

---

## 6. Estado actual

| Área | Estado |
|---|---|
| Módulos backend (catalogo, clientes, proveedores, cotizaciones, ventas, compras, inventario, mantenimiento, notificaciones, reportes, seguridad) | ✅ Funcionales |
| Facturación electrónica | ⚠️ Integración con OSE real pendiente (hoy usa cliente simulado) |
| Frontend / SPA | ❌ No iniciado |
| Modelo relacional Oracle | ✅ Corregido (ver histórico de revisiones — relaciones cabecera-detalle con dirección de FK validada) |
| Seguridad de credenciales | ⚠️ Pendiente externalizar contraseñas a variables de entorno antes de cualquier repositorio compartido |
| Control de versiones | ⚠️ Pendiente formalizar en Git/GitHub compartido entre el equipo |

---

## 7. Cómo ejecutar

1. JDK 21, Maven, Oracle XE (o perfil H2 en memoria para desarrollo).
2. Variables de entorno para credenciales (nunca en `application.properties` en texto plano):
   ```properties
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   spring.mail.password=${MAIL_PASSWORD}
   ```
3. `mvn clean install && mvn spring-boot:run`
4. Verificar: `GET /api/publico/catalogo` (sin autenticación) y `GET /api/admin/clientes` (requiere credenciales).

---

## 8. Equipo

| Integrante | Módulos a cargo |
|---|---|
| Yoel Wagner Chambi Sirena | Cotizaciones, coordinación con el cliente |
| Aron Rodrigo | Ventas, Facturación |
| Ronald Apaza | Compras, Proveedores, Inventario |
