import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { permisoGuard, rolGuard } from './core/auth/rol.guard';

export const routes: Routes = [
  // ── Retorno de Keycloak (Authorization Code + PKCE): fuera del layout y sin guard ──
  {
    path: 'auth/callback',
    loadComponent: () => import('./core/auth/auth-callback').then((m) => m.AuthCallback),
    title: 'Iniciando sesión — SGE-JD',
  },
  // El login ya no es un formulario propio: es la pantalla de Keycloak
  { path: 'login', redirectTo: '' },

  // ── Panel: Inicio es público; cada pantalla exige sesión (authGuard) y,
  //    las de escritura del catálogo, además ADMIN o el permiso CATALOGO (permisoGuard), igual que SecurityConfig ──
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then((m) => m.Layout),
    children: [
      {
        path: '',
        loadComponent: () => import('./core/inicio/inicio').then((m) => m.Inicio),
        title: 'Inicio — SGE-JD',
      },
      // ── Catálogo ───────────────────────────────────────
      {
        path: 'catalogo/categorias',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-list').then((m) => m.CategoriaList),
        title: 'Categorías — SGE-JD',
      },
      {
        path: 'catalogo/categorias/nueva',
        canActivate: [authGuard, permisoGuard('CATALOGO')],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Nueva categoría — SGE-JD',
      },
      {
        path: 'catalogo/categorias/:id/editar',
        canActivate: [authGuard, permisoGuard('CATALOGO')],
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
        title: 'Editar categoría — SGE-JD',
      },
      {
        path: 'catalogo/productos',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-list').then((m) => m.ProductoList),
        title: 'Productos — SGE-JD',
      },
      {
        path: 'catalogo/productos/nuevo',
        canActivate: [authGuard, permisoGuard('CATALOGO')],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-form').then((m) => m.ProductoForm),
        title: 'Nuevo producto — SGE-JD',
      },
      {
        path: 'catalogo/productos/:id/editar',
        canActivate: [authGuard, permisoGuard('CATALOGO')],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-form').then((m) => m.ProductoForm),
        title: 'Editar producto — SGE-JD',
      },
      {
        path: 'catalogo/productos/:id/kardex',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/catalogo/producto/producto-kardex').then((m) => m.ProductoKardexComponent),
        title: 'Kardex — SGE-JD',
      },
      // ── Proformas ──────────────────────────────────
      {
        path: 'proformas',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-list').then((m) => m.ProformaListComponent),
        title: 'Proformas — SGE-JD',
      },
      {
        path: 'proformas/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proformas/proforma/proforma-form').then((m) => m.ProformaFormComponent),
        title: 'Nueva proforma — SGE-JD',
      },
      // ── Ventas ─────────────────────────────────────────
      {
        path: 'ventas',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/ventas/venta/venta-list').then((m) => m.VentaListComponent),
        title: 'Ventas — SGE-JD',
      },
      {
        path: 'ventas/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/ventas/venta/venta-form').then((m) => m.VentaFormComponent),
        title: 'Nueva venta — SGE-JD',
      },
      // ── Cobranza ───────────────────────────────────────
      {
        path: 'cobranza',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/cobranza/cobro/cobranza-list').then((m) => m.CobranzaListComponent),
        title: 'Cobranza — SGE-JD',
      },
      // ── Postventa ──────────────────────────────────────
      {
        path: 'postventa',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/postventa/mantenimiento/mantenimiento-list').then((m) => m.MantenimientoListComponent),
        title: 'Postventa — SGE-JD',
      },
      // ── Servicios técnicos: órdenes de trabajo, equipos instalados y contratos ──
      {
        path: 'servicios/ordenes',
        canActivate: [authGuard],
        loadComponent: () => import('./features/servicios/orden/orden-list').then((m) => m.OrdenListComponent),
        title: 'Órdenes de trabajo — SGE-JD',
      },
      {
        path: 'servicios/ordenes/nueva',
        canActivate: [authGuard],
        loadComponent: () => import('./features/servicios/orden/orden-form').then((m) => m.OrdenFormComponent),
        title: 'Nueva orden de trabajo — SGE-JD',
      },
      {
        path: 'servicios/ordenes/:id',
        canActivate: [authGuard],
        loadComponent: () => import('./features/servicios/orden/orden-detalle').then((m) => m.OrdenDetalleComponent),
        title: 'Orden de trabajo — SGE-JD',
      },
      {
        path: 'servicios/ordenes/:id/editar',
        canActivate: [authGuard],
        loadComponent: () => import('./features/servicios/orden/orden-form').then((m) => m.OrdenFormComponent),
        title: 'Editar orden de trabajo — SGE-JD',
      },
      {
        path: 'servicios/equipos',
        canActivate: [authGuard],
        loadComponent: () => import('./features/postventa/equipo/equipo-list').then((m) => m.EquipoListComponent),
        title: 'Equipos instalados — SGE-JD',
      },
      {
        path: 'servicios/contratos',
        canActivate: [authGuard],
        loadComponent: () => import('./features/postventa/contrato/contrato-list').then((m) => m.ContratoListComponent),
        title: 'Contratos de mantenimiento — SGE-JD',
      },
      // ── Compras ────────────────────────────────────────
      {
        path: 'compras',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/compras/compra/compra-list').then((m) => m.CompraListComponent),
        title: 'Compras — SGE-JD',
      },
      {
        path: 'compras/nueva',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/compras/compra/compra-form').then((m) => m.CompraFormComponent),
        title: 'Nueva compra — SGE-JD',
      },
      // ── Clientes ───────────────────────────────────────
      {
        path: 'clientes',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-list').then((m) => m.ClienteListComponent),
        title: 'Clientes — SGE-JD',
      },
      {
        path: 'clientes/nuevo',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-form').then((m) => m.ClienteFormComponent),
        title: 'Nuevo cliente — SGE-JD',
      },
      {
        path: 'clientes/:id/editar',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-form').then((m) => m.ClienteFormComponent),
        title: 'Editar cliente — SGE-JD',
      },
      {
        path: 'clientes/:id/historial',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/clientes/cliente/cliente-historial').then((m) => m.ClienteHistorialComponent),
        title: 'Historial del cliente — SGE-JD',
      },
      // ── Proveedores ────────────────────────────────────
      {
        path: 'proveedores',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-list').then((m) => m.ProveedorListComponent),
        title: 'Proveedores — SGE-JD',
      },
      {
        path: 'proveedores/nuevo',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-form').then((m) => m.ProveedorFormComponent),
        title: 'Nuevo proveedor — SGE-JD',
      },
      {
        path: 'proveedores/:id/editar',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-form').then((m) => m.ProveedorFormComponent),
        title: 'Editar proveedor — SGE-JD',
      },
      {
        path: 'proveedores/:id/historial',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/proveedores/proveedor/proveedor-historial').then((m) => m.ProveedorHistorialComponent),
        title: 'Historial del proveedor — SGE-JD',
      },
      // ── Configuración (engranaje del riel): cuenta, preferencias, tipo de cambio y ayuda ──
      {
        path: 'configuracion',
        canActivate: [authGuard],
        loadComponent: () => import('./core/configuracion/configuracion').then((m) => m.ConfiguracionComponent),
        title: 'Configuración — SGE-JD',
      },
      // Solo el dueño: quién puede qué y el historial de cambios (no se delega, como en SecurityConfig)
      {
        path: 'configuracion/permisos',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () => import('./core/configuracion/permisos').then((m) => m.PermisosComponent),
        title: 'Usuarios y permisos — SGE-JD',
      },
      {
        path: 'configuracion/historial',
        canActivate: [authGuard, rolGuard('ADMIN')],
        loadComponent: () => import('./core/configuracion/historial').then((m) => m.HistorialComponent),
        title: 'Historial de cambios — SGE-JD',
      },
      // Destino de rolGuard cuando el rol no alcanza
      {
        path: 'sin-permiso',
        canActivate: [authGuard],
        loadComponent: () => import('./core/sin-permiso/sin-permiso').then((m) => m.SinPermiso),
        title: 'Acceso denegado — SGE-JD',
      },
    ],
  },

  // ── Wildcard ────────────────────────────────────────────
  { path: '**', redirectTo: '' },
];
