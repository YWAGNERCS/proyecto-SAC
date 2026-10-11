import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Protege todas las rutas del panel administrativo.
 * Si el usuario no está autenticado, redirige a /login
 * guardando la URL de destino para reencaminar después del login.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.estaLogueado()) {
    return true;
  }

  // Guarda la URL destino para redirigir después del login
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};
