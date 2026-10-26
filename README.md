<div align="center">

# 🖥️ SGE-JD · Panel administrativo (SPA)

**Frontend Angular de SGE-JD, el sistema de gestión de JD Refrigeración S.A.C.**

![Angular](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-6.0-3178C6?logo=typescript&logoColor=white)
![Vitest](https://img.shields.io/badge/tests-Vitest-6E9F18?logo=vitest&logoColor=white)
![Estado](https://img.shields.io/badge/LP2-Unidad%20II%20cerrada-2E7D5B)

</div>

> Backend: rama `master` del mismo repositorio (`bomerp-backend`, Spring Boot + Oracle). La documentación funcional completa vive allí (`README.md`, `Contexto.md` y `docs/README.md`) y está publicada en https://ywagnercs.github.io/proyecto-SAC/, junto con el producto de LP2 ([`docs/lp2-demo.md`](docs/lp2-demo.md)).

---

## ✨ Pantallas

| Módulo | Ruta | Qué permite |
|---|---|---|
| 🔐 Seguridad | `/auth/callback`, `/sin-permiso` | Login en la pantalla de Keycloak (Authorization Code + PKCE, sin formulario propio). Inicio es público; cada pantalla exige sesión (`authGuard`) y las de escritura del catálogo, además, rol ADMIN (`rolGuard`) |
| 🏠 Inicio | `/` | Panel de bienvenida |
| 📦 Catálogo | `/catalogo/productos`, `/catalogo/productos/:id/kardex`, `/catalogo/categorias` | Productos con categoría, **kardex** por producto (Excel) y CRUD de categorías |
| 📄 Proformas *(antes Cotizaciones)* | `/proformas`, `/proformas/nueva` | Listado con filtro por cliente, nueva proforma con **descuento por línea**, enviar (muestra si salió), aceptar, rechazar (con confirmación) y **convertir en venta** (contado o crédito) |
| 🛒 Ventas | `/ventas`, `/ventas/nueva` | Venta directa cabecera-detalle con descuento por línea y **reporte** con filtros, totales, detalle, anulación (solo ADMIN; avisa si hay que devolver dinero), **Excel** y **PDF del comprobante** |
| 💰 Cobranza | `/cobranza` | Ventas a crédito con saldo y días de atraso, **abonos** parciales o totales con `FormArray` (un pago puede cubrir varias ventas), historial de cobros y **devoluciones** de ventas anuladas (las registra el ADMIN) |
| 🔧 Postventa | `/postventa` | Agenda de mantenimientos: vencidos, próximos, realizados; marcar realizado y reprogramar; contacto por WhatsApp |
| 🏭 Compras | `/compras`, `/compras/nueva` | Registro de compras; listado con filtros por fecha y proveedor y **Excel** |
| 👥 Clientes | `/clientes`, `/clientes/nuevo`, `/clientes/:id/historial` | Nuevo/editar con búsqueda RUC/DNI, eliminar e **historial** (ventas, total comprado, pendiente de pago, Excel) |
| 🚚 Proveedores | `/proveedores`, `/proveedores/nuevo`, `/proveedores/:id/historial` | Nuevo/editar con búsqueda RUC/DNI, eliminar e **historial** de compras (Excel) |

*Pendientes en la SPA:* pantallas de inventario (alertas) y facturación (comprobantes y notas de crédito).

---

## 🚀 Ejecutar

**Requisitos:** Node.js, el backend en `http://localhost:8080` y Keycloak en `http://localhost:38080` (ambos se levantan desde la rama `master`).

```bash
npm install
```

```bash
npm start
```

➡️ `http://localhost:4200` · Usuarios de desarrollo: `lisandro` (ADMIN) · `lucy` (ASISTENTE) (cuentas de Keycloak; las contraseñas están en `bomerp-backend/.env` (no se versiona; plantilla en `.env.example`)).

| Comando | Uso |
|---|---|
| `npm start` | Servidor de desarrollo con recarga automática |
| `npm run build` | Compilación de producción en `dist/` |
| `npm test` | Pruebas unitarias con Vitest |

La URL del backend (`apiBaseUrl`) y la de Keycloak (`keycloak.url`, `realm`, `clientId`) se configuran en `src/environments/environment.ts`.

---

## 🏗️ Arquitectura

SPA **standalone** (sin NgModules), con **signals**, detección de cambios **zoneless** y carga diferida por ruta (`loadComponent`).

```text
src/app/
├── core/
│   ├── auth/            AuthService (PKCE, callback, refresco, logout), pkce.ts, jwt.ts, auth-callback, authGuard, rolGuard
│   ├── interceptors/    jwt-auth (Authorization: Bearer; ante 401 refresca una vez y reintenta), trace-id (X-Trace-ID)
│   ├── sin-permiso/     destino de rolGuard
│   ├── layout/          barra de navegación (menú ☰ en pantallas angostas), usuario, rol y "Salir"
│   ├── inicio/          página de inicio
│   └── services/        ApiService, DescargaService, TipoCambioService, DescuentoService
├── features/            un directorio por módulo de negocio
│   ├── catalogo/        categoria/, producto/
│   ├── clientes/        cliente/
│   ├── cobranza/        cobro/ (saldos, abonos, historial y devoluciones)
│   ├── compras/         compra/
│   ├── postventa/       mantenimiento/ (agenda de mantenimientos)
│   ├── proformas/       proforma/ (antes cotizaciones/)
│   ├── proveedores/     proveedor/
│   └── ventas/          venta/ (formulario cabecera-detalle y reporte)
```

**Convenciones**
- Cada funcionalidad tiene su `*.model.ts` (refleja exactamente los DTO del backend), `*-service.ts` (HTTP) y sus componentes.
- Los montos que calcula el formulario son una **vista previa**: el backend calcula los definitivos y la SPA solo envía ids y cantidades.
- Los errores del backend se muestran por código: `400` datos inválidos · `404` no existe · `409` regla de negocio o stock insuficiente.

**Diseño responsivo:** los estilos globales (`src/styles.css`) tienen tres cortes: hasta 640 px (celular: una columna, botones y encabezados que bajan de línea, tablas con desplazamiento horizontal dentro de su tarjeta), hasta 900 px (tablet) y hasta 1280 px (el menú pasa a ☰). Cada pantalla ajusta lo suyo en su `.css`; por ejemplo, el detalle de Nueva venta se muestra como tarjetas en el celular para no recortar el buscador de productos.

**Seguridad (Keycloak, S10b + S11):**
- **Login:** `AuthService.iniciarLogin()` genera `code_verifier`, `code_challenge` (S256) y `state`, y redirige a Keycloak; `/auth/callback` comprueba el `state` y canjea el código con el verifier. La SPA nunca ve la contraseña. El cliente `bomerp-frontend` exige PKCE y no permite *Direct access grants*.
- **Dónde vive el token:** en memoria (signal), con una copia espejo en `sessionStorage` para sobrevivir un F5 en la misma pestaña; nunca en `localStorage`. El `code_verifier` y el `state` se borran apenas se usan.
- **Guards:** `authGuard` lleva al login de Keycloak y vuelve a la ruta pedida; `rolGuard('ADMIN')` lleva a `/sin-permiso` (crear o editar productos y categorías).
- **Interceptor:** adjunta `Authorization: Bearer` a las peticiones al backend; ante un **401** refresca el token **una sola vez** (un único refresco compartido aunque fallen varias peticiones a la vez) y reintenta; ante un **403** no refresca.
- **Menú por rol:** sin sesión solo se ve "Iniciar sesión"; con sesión, las secciones; el ADMIN ve además "+ Producto". Los botones de modificar el catálogo, anular ventas y eliminar clientes o proveedores solo aparecen para ADMIN (el backend responde 403 de todas formas).

---

## 🗺️ Avance (LP2)

- [x] **S7** · Proyecto Angular, layout, rutas, servicios HTTP
- [x] **S8** · CRUD de tablas dependientes
- [x] **S9** · Formulario transaccional de venta, reporte filtrable y anulación (el mismo `FormArray` en proforma, compra y cobro)
- [x] **S10** · Login con Keycloak, sesión en `sessionStorage`, renovación del token, interceptor Bearer y botones según el rol
- [x] Diseño responsivo (celular, tablet y escritorio)
- [x] **S11** · Login con Authorization Code + PKCE, guards por sesión y rol, refresco ante 401, menú por rol
- [x] **S12** · Integración de la SPA segura (Producto U2): [`docs/lp2-demo.md`](docs/lp2-demo.md), publicado en https://ywagnercs.github.io/proyecto-SAC/

---

## 👥 Equipo · CherryTree

| Integrante | Módulos a cargo (backend y SPA) |
|---|---|
| **Yoel Wagner Chambi Sirena** | Proveedores · Proformas · Ventas |
| **Wilson Ronald Apaza Mamani** | Catálogo · Inventario · Compras · Postventa |
| **Aron Rodrigo Magaño Quispe** | Clientes · Facturación · Notificaciones · Cobranza |

Seguridad: Keycloak, integrado en S10b y S11.

<div align="center">
<sub>Proyecto Integrador ADS · BD2 · LP2 — Universidad Peruana Unión · Generado con Angular CLI 22.2</sub>
</div>
