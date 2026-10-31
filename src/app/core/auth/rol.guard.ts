import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Permiso } from './auth.service';

/**
 * Fábrica de guards por rol: rolGuard('ADMIN') deja pasar solo a quien tenga ese rol y redirige
 * a /sin-permiso al resto (no un bloqueo en silencio). Va DESPUÉS de authGuard en canActivate:
 * sin sesión, primero hay que ofrecer el login.
 * Refleja las mismas reglas que SecurityConfig exige en el backend; el guard es comodidad,
 * la seguridad real sigue siendo el 403 del servidor.
 */
export function rolGuard(...rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.tieneRol(...rolesPermitidos) ? true : inject(Router).parseUrl('/sin-permiso');
  };
}

/** Como rolGuard, pero por permiso delegable: ADMIN o quien tenga PERM_<permiso>. */
export function permisoGuard(permiso: Permiso): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.puede(permiso) ? true : inject(Router).parseUrl('/sin-permiso');
  };
}
