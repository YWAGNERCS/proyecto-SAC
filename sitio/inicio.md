# SGE-JD · Sistema de Gestión Empresarial

**Sistema web de JD Refrigeración S.A.C.** (Breña, Lima): proformas, ventas, compras, cobranza,
facturación y postventa en un solo lugar. Es el Proyecto Integrador de ADS, BD2 y LP2 de la
Universidad Peruana Unión, construido sobre la base académica BomERP.

{{ENCABEZADO}}

## Qué hay en esta documentación

| Sección | Contenido |
|---|---|
| [1. Documentación funcional](funcional.md) | Objetivo, alcance, módulos, requerimientos (RF y RNF) y casos de uso |
| [2. Documentación técnica](tecnica.md) | Stack, migración a Spring Boot 4.0.7, arquitectura, modelo de datos, API, instalación y uso diario |
| [3. Manual de usuario](manual.md) | Cómo se opera cada pantalla y preguntas frecuentes |
| [4. Estado y trazabilidad](estado.md) | Fases del proyecto, equipo y avance por sesión de LP2 |
| [5. Por verificar](por-verificar.md) | Lo que falta o no coincide, ordenado por importancia |
| [Producto de la Unidad II (LP2)](lp2-demo.md) | La SPA segura: evidencias, casos de prueba, integración y rúbrica |
| [Qué hace hoy y qué todavía no](limitaciones.md) | Para Lisandro y Lucy: lo que funciona de verdad, lo simulado y las decisiones pendientes |

## Equipo CherryTree

| Integrante | Módulos | Transaccional (muchos a muchos) |
|---|---|---|
| **Yoel Wagner Chambi Sirena** | Proveedores, Proformas y Ventas | Proforma ↔ Producto (`ItemProforma`) · Venta ↔ Producto (`DetalleVenta`) |
| **Wilson Ronald Apaza Mamani** | Catálogo, Inventario, Compras y Postventa | Compra ↔ Producto (`DetalleCompra`) |
| **Aron Rodrigo Magaño Quispe** | Clientes, Facturación, Notificaciones y Cobranza | Cobro ↔ Venta (`DetalleCobro`) |

Cada muchos a muchos se resuelve con una **tabla intermedia** (entre paréntesis) en la que cada fila es una línea con sus propios datos: cantidad, precio, descuento o monto abonado. Explicación con diagrama y ejemplo en [Relaciones muchos a muchos](tecnica.md#relaciones-muchos-a-muchos).

## Tecnologías

Java 21 · Spring Boot 4.0.7 · Oracle 23 Free · Angular 22 · Keycloak 26.8 · Docker
