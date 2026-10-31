import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Permiso } from '../auth/auth.service';
import { PermisoCatalogo, SeguridadService, UsuarioPermisos } from '../services/seguridad-service';
import { iniciales, tonoAvatar } from '../layout/menu';

/**
 * Configuración › Usuarios y permisos (solo ADMIN). El dueño enciende o apaga, por asistente, cada
 * operación que normalmente es solo suya. Se guarda en Keycloak como rol PERM_*; el asistente lo ve
 * al renovarse su sesión (5 minutos como máximo) o al volver a entrar.
 */
@Component({
  selector: 'app-permisos',
  imports: [RouterLink],
  templateUrl: './permisos.html',
  styleUrl: './permisos.css',
})
export class PermisosComponent {
  private readonly seguridad = inject(SeguridadService);
  readonly iniciales = iniciales;
  readonly tonoAvatar = tonoAvatar;

  readonly catalogo = signal<PermisoCatalogo[]>([]);
  readonly usuarios = signal<UsuarioPermisos[]>([]);
  /** Lo marcado en pantalla y aún no guardado, por usuario. */
  readonly borrador = signal<Record<string, Permiso[]>>({});
  readonly cargando = signal(true);
  readonly guardando = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly aviso = signal<string | null>(null);

  constructor() {
    this.cargar();
  }

  cargar() {
    this.cargando.set(true);
    this.error.set(null);
    this.seguridad.permisos().subscribe({
      next: (d) => {
        this.catalogo.set(d.catalogo);
        this.usuarios.set(d.usuarios);
        this.borrador.set(Object.fromEntries(d.usuarios.map((u) => [u.username, [...u.permisos]])));
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'No se pudieron cargar los usuarios.');
        this.cargando.set(false);
      },
    });
  }

  marcado(u: UsuarioPermisos, p: Permiso): boolean {
    return u.rol === 'ADMIN' || (this.borrador()[u.username] ?? []).includes(p);
  }

  alternar(u: UsuarioPermisos, p: Permiso) {
    this.aviso.set(null);
    this.borrador.update((b) => {
      const actuales = b[u.username] ?? [];
      return { ...b, [u.username]: actuales.includes(p) ? actuales.filter((x) => x !== p) : [...actuales, p] };
    });
  }

  hayCambios(u: UsuarioPermisos): boolean {
    const b = [...(this.borrador()[u.username] ?? [])].sort();
    const g = [...u.permisos].sort();
    return b.length !== g.length || b.some((x, i) => x !== g[i]);
  }

  descartar(u: UsuarioPermisos) {
    this.borrador.update((b) => ({ ...b, [u.username]: [...u.permisos] }));
  }

  guardar(u: UsuarioPermisos) {
    this.guardando.set(u.username);
    this.error.set(null);
    this.aviso.set(null);
    this.seguridad.guardarPermisos(u.username, this.borrador()[u.username] ?? []).subscribe({
      next: (actualizado) => {
        this.usuarios.update((l) => l.map((x) => (x.username === actualizado.username ? actualizado : x)));
        this.borrador.update((b) => ({ ...b, [actualizado.username]: [...actualizado.permisos] }));
        this.guardando.set(null);
        this.aviso.set(`Permisos de ${actualizado.nombre} guardados. Los verá en su sesión en 5 minutos como máximo, o al volver a entrar.`);
      },
      error: (err) => {
        this.guardando.set(null);
        this.error.set(err.error?.message ?? 'No se pudieron guardar los permisos.');
      },
    });
  }
}
