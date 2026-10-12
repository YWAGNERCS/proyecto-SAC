<div align="center">

# 🖥️ SGE-JD · Panel administrativo (SPA)

**Frontend Angular de SGE-JD, el sistema de gestión de JD Refrigeración S.A.C.**

![Angular](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-6.0-3178C6?logo=typescript&logoColor=white)
![Vitest](https://img.shields.io/badge/tests-Vitest-6E9F18?logo=vitest&logoColor=white)
![Estado](https://img.shields.io/badge/estado-en%20desarrollo-yellow)

</div>

> Backend: rama `master` del mismo repositorio (`bomerp-backend`, Spring Boot + Oracle). La documentación funcional completa vive allí (`README.md`, `CONTEXTO.md` y `src/main/resources/static/docs/README.md`).

---

## ✨ Pantallas

| Módulo | Ruta | Qué permite |
|---|---|---|
| 🔐 Seguridad | `/login` | Inicio de sesión contra el backend; las demás rutas están protegidas por un guard |
| 🏠 Inicio | `/` | Panel de bienvenida |
| 📦 Catálogo | `/catalogo/categorias` | CRUD de categorías |
| 📄 Cotizaciones | `/cotizaciones`, `/cotizaciones/nuevo` | Listado, nueva cotización, aceptar/rechazar/enviar |
| 🛒 Ventas | `/ventas`, `/ventas/nueva` | **Venta directa cabecera-detalle** (FormArray, búsqueda de producto con precio, totales en vivo, confirmación) y **reporte** con filtros por estado y fechas, totales, detalle y **anulación** |
| 🏭 Compras | `/compras`, `/compras/nueva` | Registro de compras a proveedor |
| 👥 Clientes | `/clientes` | Listado de clientes |
| 🚚 Proveedores | `/proveedores` | Listado de proveedores |

*Pendientes en la SPA:* registro y edición de clientes y proveedores; pantallas de posventa (mantenimientos), inventario y facturación.

---

## 🚀 Ejecutar

**Requisitos:** Node.js y el backend corriendo en `http://localhost:8080` (ver la rama `master`).

```bash
npm install
```

```bash
npm start
```

➡️ `http://localhost:4200` · Usuarios de desarrollo: `admin` / `asistente`, contraseña `123456`.

| Comando | Uso |
|---|---|
| `npm start` | Servidor de desarrollo con recarga automática |
| `npm run build` | Compilación de producción en `dist/` |
| `npm test` | Pruebas unitarias con Vitest |

La URL del backend se configura en `src/environments/environment.ts` (`apiBaseUrl`).

---

## 🏗️ Arquitectura

SPA **standalone** (sin NgModules), con **signals**, detección de cambios **zoneless** y carga diferida por ruta (`loadComponent`).

```text
src/app/
├── core/
│   ├── auth/            AuthService (sesión con signals), authGuard
│   ├── interceptors/    basic-auth (credenciales en /admin/), trace-id (X-Trace-ID)
│   ├── layout/          barra de navegación, usuario y "Salir"
│   ├── inicio/          página de inicio
│   └── services/        ApiService (construye las URLs del backend)
├── features/            un directorio por módulo de negocio
│   ├── auth/            login
│   ├── catalogo/        categoria/, producto/
│   ├── clientes/        cliente/
│   ├── compras/         compra/
│   ├── cotizaciones/    cotizacion/
│   ├── proveedores/     proveedor/
│   └── ventas/          venta/ (formulario cabecera-detalle y reporte)
└── shared/              componentes reutilizables (spinner)
```

**Convenciones**
- Cada funcionalidad tiene su `*.model.ts` (refleja exactamente los DTO del backend), `*-service.ts` (HTTP) y sus componentes.
- Los montos que calcula el formulario son una **vista previa**: el backend calcula los definitivos y la SPA solo envía ids y cantidades.
- Los errores del backend se muestran por código: `400` datos inválidos · `404` no existe · `409` regla de negocio o stock insuficiente.

**Seguridad (transitoria):** el login valida las credenciales con `GET /api/v1/admin/auth/me` y el interceptor adjunta `Authorization: Basic` a las rutas `/admin/`. En **S10–S11** se reemplaza por JWT, con expiración, menú según permisos y manejo de 401/403.

---

## 🗺️ Avance (LP2)

- [x] **S7** · Proyecto Angular, layout, rutas, servicios HTTP
- [x] **S8** · CRUD de tablas dependientes
- [x] **S9** · Formulario transaccional de venta, reporte filtrable y anulación
- [ ] **S11** · Seguridad frontend: JWT, expiración, menú por permisos, 401/403
- [ ] **S12** · Integración de la SPA segura (Producto U2)

---

## 👥 Equipo

| Integrante | Módulos a cargo (backend y SPA) |
|---|---|
| **Yoel Wagner Chambi Sirena** | Proveedores · Cotizaciones · Ventas · Posventa |
| **Wilson Ronald Apaza Mamani** | Catálogo · Inventario · Compras |
| **Aron Rodrigo Magaño Quispe** | Clientes · Facturación · Notificaciones · Seguridad |

<div align="center">
<sub>Proyecto Integrador ADS · BD2 · LP2 — Universidad Peruana Unión · Generado con Angular CLI 22.2</sub>
</div>
