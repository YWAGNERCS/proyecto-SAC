import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { PreferenciasService } from '../services/preferencias-service';
import { BuscadorComponent } from './buscador';
import { AlertasComponent } from './alertas';
import { GRUPOS, GrupoMenu, ICONOS, grupoDeUrl, iniciales } from './menu';

/**
 * Estructura de la SPA (referencia "Quixotic"):
 *  - Barra superior: marca, grupos en píldoras, buscador global, avisos y menú del usuario.
 *  - Riel izquierdo: los módulos del grupo activo, como íconos con su nombre al pasar el mouse.
 *  - En tablet y celular el riel se oculta y ☰ abre el menú completo con texto.
 * Inicio es público; el resto del menú aparece solo con sesión. Las opciones solo ADMIN
 * siguen la misma regla que SecurityConfig y rolGuard.
 */
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BuscadorComponent, AlertasComponent],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class Layout {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  /** Se crea al arrancar para aplicar el tamaño de letra guardado. */
  private readonly preferencias = inject(PreferenciasService);

  readonly grupos = GRUPOS;
  readonly iconos = ICONOS;
  readonly iniciales = iniciales;

  private readonly url = signal(this.router.url);
  /** Grupo de la pantalla actual: su píldora queda activa y el riel muestra sus módulos. */
  readonly grupoActivo = computed<GrupoMenu>(() => grupoDeUrl(this.url()));
  readonly opcionesRiel = computed(() =>
    this.grupoActivo().opciones.filter((o) => !o.soloAdmin || this.auth.tieneRol('ADMIN')));

  /** Menú completo desplegado (tablet y celular). */
  readonly menuAbierto = signal(false);
  /** Menú del usuario (avatar). */
  readonly usuarioAbierto = signal(false);

  constructor() {
    // Al cambiar de pantalla los menús se cierran y el grupo activo se actualiza
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.url.set((e as NavigationEnd).urlAfterRedirects);
      this.menuAbierto.set(false);
      this.usuarioAbierto.set(false);
    });
  }

  /** Primera opción visible del grupo (a donde lleva su píldora). */
  destinoDe(g: GrupoMenu): string {
    return g.opciones.find((o) => !o.soloAdmin || this.auth.tieneRol('ADMIN'))?.ruta ?? '/';
  }

  visibles(g: GrupoMenu) {
    return g.opciones.filter((o) => (!o.soloAdmin || this.auth.tieneRol('ADMIN')) && !(g.id === 'inicio' && o.ruta !== '/'));
  }

  /** La opción del riel está activa si la URL es suya (las rutas más cortas no "ganan" a las largas). */
  activa(ruta: string): boolean {
    const actual = this.url().split('?')[0];
    if (ruta === '/') return actual === '/';
    if (actual !== ruta && !actual.startsWith(ruta + '/')) return false;
    // "/catalogo/productos" no se marca cuando se está en "/catalogo/productos/nuevo" (tiene su propio ícono)
    return !this.opcionesRiel().some((o) => o.ruta !== ruta && o.ruta.startsWith(ruta) && (actual === o.ruta || actual.startsWith(o.ruta + '/')));
  }

  alternarMenu(): void {
    this.menuAbierto.update((v) => !v);
  }

  alternarUsuario(): void {
    this.usuarioAbierto.update((v) => !v);
  }

  @HostListener('document:click', ['$event'])
  clicAfuera(e: MouseEvent) {
    const menuUsuario = this.host.nativeElement.querySelector('.usuario');
    if (menuUsuario && !menuUsuario.contains(e.target as Node)) this.usuarioAbierto.set(false);
  }

  @HostListener('document:keydown.escape')
  escape() {
    this.usuarioAbierto.set(false);
    this.menuAbierto.set(false);
  }

  logout(): void {
    this.auth.cerrarSesion();
  }
}
