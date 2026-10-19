import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Adjunta "Authorization: Bearer <access token>" a cada petición al backend (no al catálogo
 * público ni a Keycloak).
 *  - 401 (token vencido o inválido): refresca UNA vez y reintenta la petición; si no se puede
 *    refrescar, vuelve al login. Nunca entra en bucle: el reintento no vuelve a refrescar.
 *  - 403 (rol insuficiente): no se refresca; un token nuevo tendría los mismos roles.
 */
export const jwtAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const alBackend = req.url.startsWith(environment.apiBaseUrl) && !req.url.includes('/publico/');
  if (!alBackend) return next(req);

  const conToken = (token: string | null) =>
    token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(conToken(auth.accessToken())).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || !auth.autenticado()) {
        return throwError(() => error);
      }
      return from(auth.refrescar()).pipe(
        switchMap((renovado) => {
          if (!renovado) {
            auth.iniciarLogin();
            return throwError(() => error);
          }
          return next(conToken(auth.accessToken())); // un solo reintento
        }),
      );
    }),
  );
};
