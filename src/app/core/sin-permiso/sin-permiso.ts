import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { IconoComponent } from '../../core/layout/icono';

/** Destino de rolGuard cuando el rol del usuario no alcanza para la pantalla pedida. */
@Component({
  selector: 'app-sin-permiso',
  imports: [IconoComponent, RouterLink],
  template: `
    <div class="card sin-permiso">
      <span class="icono" aria-hidden="true"><app-icono nombre="candado" [tamano]="40" /></span>
      <h2>Acceso denegado</h2>
      <p>
        Su usuario
        @if (auth.usuario(); as u) { (<strong>{{ u.nombre }}</strong>, rol {{ u.roles.join(', ') || 'sin rol del sistema' }}) }
        no tiene permiso para esa pantalla. Solo el administrador puede crear o modificar el catálogo.
      </p>
      <a routerLink="/" class="btn btn-primary">Volver al inicio</a>
    </div>
  `,
  styles: `
    .sin-permiso { max-width: 520px; margin: 2rem auto; padding: 32px 24px; text-align: center; }
    .icono { display: inline-flex; color: var(--text-muted); }
    h2 { margin: 8px 0; }
    p { color: var(--text-muted); margin-bottom: 20px; }
  `,
})
export class SinPermiso {
  readonly auth = inject(AuthService);
}
