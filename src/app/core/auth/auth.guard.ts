import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Exige sesión. Sin sesión no bloquea en silencio: lleva al login de Keycloak y,
 * al volver, retoma la ruta pedida (state.url).
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  if (auth.autenticado()) {
    return true;
  }
  await auth.iniciarLogin(state.url);
  return false;
};
