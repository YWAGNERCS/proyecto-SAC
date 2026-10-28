import { Component, inject, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** Una opción del menú lateral; `icono` es el trazo SVG (24×24, estilo línea). */
interface OpcionMenu {
  ruta: string;
  texto: string;
  icono: string;
  exacta?: boolean;
  soloAdmin?: boolean;
}

interface GrupoMenu {
  titulo: string | null;
  opciones: OpcionMenu[];
}

const ICONOS = {
  inicio: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  proformas: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z M14 3v5h5 M9 13h6 M9 17h6',
  ventas: 'M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.5L21 8H6.2 M9 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2 M18 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2',
  cobranza: 'M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-3 M3 7h18v4 M21 11h-5a2 2 0 0 0 0 4h5z',
  postventa: 'M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z',
  compras: 'M21 8 12 3 3 8v8l9 5 9-5z M3 8l9 5 9-5 M12 13v8',
  productos: 'M4 8h16v12H4z M2 4h20v4H2z M10 12h4',
  categorias: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z M7.5 7.5h.01',
  clientes: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.9 M16 3.1a4 4 0 0 1 0 7.8',
  proveedores: 'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16 M16 9h3a2 2 0 0 1 2 2v10 M2 21h20 M8 7h4 M8 11h4 M8 15h4',
  nuevo: 'M12 5v14 M5 12h14',
};

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class Layout {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Menú lateral desplegado en pantallas angostas (celular y tablet). */
  readonly menuAbierto = signal(false);

  /** Título de la pantalla actual (el `title` de su ruta, sin "— SGE-JD"). */
  readonly titulo = signal('Inicio');

  /** Fecha de hoy para la barra superior ("lunes, 27 de octubre"). */
  readonly hoy = new Date().toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });

  /** Mismos módulos que antes, agrupados por el trabajo del día a día. */
  readonly grupos: GrupoMenu[] = [
    { titulo: null, opciones: [{ ruta: '/', texto: 'Inicio', icono: ICONOS.inicio, exacta: true }] },
    {
      titulo: 'Comercial',
      opciones: [
        { ruta: '/proformas', texto: 'Proformas', icono: ICONOS.proformas },
        { ruta: '/ventas', texto: 'Ventas', icono: ICONOS.ventas },
        { ruta: '/cobranza', texto: 'Cobranza', icono: ICONOS.cobranza },
        { ruta: '/postventa', texto: 'Postventa', icono: ICONOS.postventa },
      ],
    },
    {
      titulo: 'Inventario',
      opciones: [
        { ruta: '/compras', texto: 'Compras', icono: ICONOS.compras },
        { ruta: '/catalogo/productos', texto: 'Productos', icono: ICONOS.productos },
        { ruta: '/catalogo/categorias', texto: 'Categorías', icono: ICONOS.categorias },
        // Solo ADMIN: misma regla que SecurityConfig (POST /productos exige ADMIN) y que rolGuard
        { ruta: '/catalogo/productos/nuevo', texto: 'Nuevo producto', icono: ICONOS.nuevo, soloAdmin: true },
      ],
    },
    {
      titulo: 'Contactos',
      opciones: [
        { ruta: '/clientes', texto: 'Clientes', icono: ICONOS.clientes },
        { ruta: '/proveedores', texto: 'Proveedores', icono: ICONOS.proveedores },
      ],
    },
  ];

  constructor() {
    this.actualizarTitulo();
    // Al cambiar de pantalla: el menú se cierra solo y el título se actualiza
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.menuAbierto.set(false);
      this.actualizarTitulo();
    });
  }

  /** Iniciales para el avatar del usuario ("Lisandro Madrid" → "LM"). */
  iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  }

  alternarMenu(): void {
    this.menuAbierto.update((v) => !v);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  logout(): void {
    this.auth.cerrarSesion();
  }

  private actualizarTitulo(): void {
    let ruta: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let titulo: string | undefined;
    while (ruta) {
      titulo = ruta.title ?? titulo;
      ruta = ruta.firstChild;
    }
    this.titulo.set((titulo ?? 'Inicio').replace(/\s*—\s*SGE-JD$/, ''));
  }
}
