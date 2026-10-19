import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { rolGuard } from './core/auth/rol.guard';

export const routes: Routes = [
  // ── Retorno de Keycloak (Authorization Code + PKCE): fuera del layout y sin guard ──
  {
    path: 'auth/callback',
    loadComponent: () => import('./core/auth/auth-callback').then((m) => m.AuthCallback),
    title: 'Iniciando sesión — BomERP',
  },
  // El login ya no es un formulario propio: es la pantalla de Keycloak
  { path: 'login', redirectTo: '' },

  // ── Panel: Inicio es público; cada pantalla exige sesión (authGuard) y,
  //    las de escritura del catálogo, además el rol ADMIN (rolGuard), igual que SecurityConfig ──
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then((m) => m.Layout),
    children: [
      {
        path: '',
        loadComponent: () => import('./core/inicio/inicio').then((m) => m.Inicio),
        title: 'Inicio — BomERP',
      },
      // ── Catálogo ───────────────────────────────────────
      {
        path: 'catalogo/categorias',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-list').then((m) => m.CategoriaList),
        title: 'Categorías — BomERP',
      },
      {
        path: 'catalogo/categorias/nueva',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Nueva categoría — BomERP',
      },
      {
        path: 'catalogo/categorias/:id/editar',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Editar categoría — BomERP',
      },
      {
        path: 'catalogo/productos',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-list').then((m) => m.ProductoList),
        title: 'Productos — BomERP',
      },
      {
        path: 'catalogo/productos/nuevo',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-form').then((m) => m.ProductoForm),
        title: 'Nuevo producto — BomERP',
      },
      {
        path: 'catalogo/productos/:id/editar',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-form').then((m) => m.ProductoForm),
        title: 'Editar producto — BomERP',
      },
      {
        path: 'catalogo/productos/:id/kardex',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-kardex').then((m) => m.ProductoKardexComponent),
        title: 'Kardex — BomERP',
      },
      // ── Proformas ──────────────────────────────────
      {
        path: 'proformas',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-list').then((m) => m.ProformaListComponent),
        title: 'Proformas — BomERP',
      },
      {
        path: 'proformas/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-form').then((m) => m.ProformaFormComponent),
        title: 'Nueva proforma — BomERP',
      },
      // ── Ventas ─────────────────────────────────────────
      {
        path: 'ventas',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/ventas/venta/venta-list').then((m) => m.VentaListComponent),
        title: 'Ventas — BomERP',
      },
      {
        path: 'ventas/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/ventas/venta/venta-form').then((m) => m.VentaFormComponent),
        title: 'Nueva venta — BomERP',
      },
      // ── Cobranza ───────────────────────────────────────
      {
        path: 'cobranza',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/cobranza/cobro/cobranza-list').then((m) => m.CobranzaListComponent),
        title: 'Cobranza — BomERP',
      },
      // ── Postventa ──────────────────────────────────────
      {
        path: 'postventa',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/postventa/mantenimiento/mantenimiento-list').then((m) => m.MantenimientoListComponent),
        title: 'Postventa — BomERP',
      },
      // ── Compras ────────────────────────────────────────
      {
        path: 'compras',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/compras/compra/compra-list').then((m) => m.CompraListComponent),
        title: 'Compras — BomERP',
      },
      {
        path: 'compras/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/compras/compra/compra-form').then((m) => m.CompraFormComponent),
        title: 'Nueva compra — BomERP',
      },
      // ── Clientes ───────────────────────────────────────
      {
        path: 'clientes',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-list').then((m) => m.ClienteListComponent),
        title: 'Clientes — BomERP',
      },
      {
        path: 'clientes/nuevo',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-form').then((m) => m.ClienteFormComponent),
        title: 'Nuevo cliente — BomERP',
      },
      {
        path: 'clientes/:id/editar',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-form').then((m) => m.ClienteFormComponent),
        title: 'Editar cliente — BomERP',
      },
      {
        path: 'clientes/:id/historial',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-historial').then((m) => m.ClienteHistorialComponent),
        title: 'Historial del cliente — BomERP',
      },
      // ── Proveedores ────────────────────────────────────
      {
        path: 'proveedores',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-list').then((m) => m.ProveedorListComponent),
        title: 'Proveedores — BomERP',
      },
      {
        path: 'proveedores/nuevo',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-form').then((m) => m.ProveedorFormComponent),
        title: 'Nuevo proveedor — BomERP',
      },
      {
        path: 'proveedores/:id/editar',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-form').then((m) => m.ProveedorFormComponent),
        title: 'Editar proveedor — BomERP',
      },
      {
        path: 'proveedores/:id/historial',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-historial').then((m) => m.ProveedorHistorialComponent),
        title: 'Historial del proveedor — BomERP',
      },
      // Destino de rolGuard cuando el rol no alcanza
      {
        path: 'sin-permiso',
        canActivate: [authGuard],
        loadComponent: () => import('./core/sin-permiso/sin-permiso').then((m) => m.SinPermiso),
        title: 'Acceso denegado — BomERP',
      },
    ],
  },

  // ── Wildcard ────────────────────────────────────────────
  { path: '**', redirectTo: '' },
];
