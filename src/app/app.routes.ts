import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then((m) => m.Layout),
    children: [
      {
        path: '',
        loadComponent: () => import('./core/inicio/inicio').then((m) => m.Inicio),
      },
      {
        path: 'catalogo/categorias',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-list').then((m) => m.CategoriaList),
      },
      {
        path: 'catalogo/categorias/nueva',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
      },
      {
        path: 'catalogo/categorias/:id/editar',
        loadComponent: () =>
          import('./features/catalogo/categoria/categoria-form').then((m) => m.CategoriaForm),
      },
      {
        path: 'cotizaciones',
        loadComponent: () =>
          import('./features/cotizaciones/cotizacion/cotizacion-list').then((m) => m.CotizacionListComponent),
      },
      {
        path: 'cotizaciones/nuevo',
        loadComponent: () =>
          import('./features/cotizaciones/cotizacion/cotizacion-form').then((m) => m.CotizacionFormComponent),
      },
    ],
  },
];
