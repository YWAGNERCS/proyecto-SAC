import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Ruta de retorno de Keycloak (/auth/callback?code=…&state=…): canjea el código por los tokens
 * y lleva al usuario a donde quería ir. Va fuera del layout y sin guard: aquí todavía no hay sesión.
 */
@Component({
  selector: 'app-auth-callback',
  template: `
    <div class="callback">
      @if (error()) {
        <p class="callback-error">{{ error() }}</p>
        <button type="button" class="btn btn-primary" (click)="reintentar()">Iniciar sesión de nuevo</button>
      } @else {
        <p>Completando el inicio de sesión…</p>
      }
    </div>
  `,
  styles: `
    .callback { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 24px; text-align: center; color: var(--text-muted); }
    .callback-error { color: #B91C1C; max-width: 420px; }
  `,
})
export class AuthCallback {
  private readonly auth = inject(AuthService);
  readonly error = signal('');

  constructor() {
    const route = inject(ActivatedRoute);
    const router = inject(Router);
    const parametros = route.snapshot.queryParamMap;
    const codigo = parametros.get('code');
    const state = parametros.get('state');
    const errorKeycloak = parametros.get('error_description') ?? parametros.get('error');

    if (errorKeycloak || !codigo || !state) {
      this.error.set(`Keycloak no completó el inicio de sesión${errorKeycloak ? ': ' + errorKeycloak : '.'}`);
      return;
    }
    this.auth
      .completarLogin(codigo, state)
      .then(() => router.navigateByUrl(this.auth.destinoPendiente(), { replaceUrl: true }))
      .catch((e: Error) => this.error.set(e.message));
  }

  reintentar(): void {
    this.auth.iniciarLogin('/');
  }
}
