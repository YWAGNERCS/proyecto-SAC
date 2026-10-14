import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // ── Login (fuera del layout protegido) ──────────────────
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login').then((m) => m.LoginComponent),
    title: 'Iniciar sesión — BomERP',
  },

  // ── Panel administrativo (protegido por AuthGuard) ──────
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then((m) => m.Layout),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./core/inicio/inicio').then((m) => m.Inicio),
        title: 'Inicio — BomERP',
      },
      // ── Catálogo ───────────────────────────────────────
      {
        path: 'catalogo/categorias',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-list').then((m) => m.CategoriaList),
        title: 'Categorías — BomERP',
      },
      {
        path: 'catalogo/categorias/nueva',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Nueva categoría — BomERP',
      },
      {
        path: 'catalogo/categorias/:id/editar',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Editar categoría — BomERP',
      },
      // ── Proformas ──────────────────────────────────
      {
        path: 'proformas',
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-list').then((m) => m.ProformaListComponent),
        title: 'Proformas — BomERP',
      },
      {
        path: 'proformas/nueva',
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-form').then((m) => m.ProformaFormComponent),
        title: 'Nueva proforma — BomERP',
      },
      // ── Ventas ─────────────────────────────────────────
      {
        path: 'ventas',
        loadComponent: () =>
          import('./features/ventas/venta/venta-list').then((m) => m.VentaListComponent),
        title: 'Ventas — BomERP',
      },
      {
        path: 'ventas/nueva',
        loadComponent: () =>
          import('./features/ventas/venta/venta-form').then((m) => m.VentaFormComponent),
        title: 'Nueva venta — BomERP',
      },
      // ── Compras ────────────────────────────────────────
      {
        path: 'compras',
        loadComponent: () =>
          import('./features/compras/compra/compra-list').then((m) => m.CompraListComponent),
        title: 'Compras — BomERP',
      },
      {
        path: 'compras/nueva',
        loadComponent: () =>
          import('./features/compras/compra/compra-form').then((m) => m.CompraFormComponent),
        title: 'Nueva compra — BomERP',
      },
      // ── Clientes ───────────────────────────────────────
      {
        path: 'clientes',
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-list').then((m) => m.ClienteListComponent),
        title: 'Clientes — BomERP',
      },
      // ── Proveedores ────────────────────────────────────
      {
        path: 'proveedores',
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-list').then((m) => m.ProveedorListComponent),
        title: 'Proveedores — BomERP',
      },
    ],
  },

  // ── Wildcard ────────────────────────────────────────────
  { path: '**', redirectTo: '' },
];
