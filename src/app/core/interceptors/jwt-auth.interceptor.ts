import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Adjunta "Authorization: Bearer <access token de Keycloak>" a las peticiones al backend
 * (no al catálogo público ni a Keycloak). Si el backend responde 401, la sesión ya no sirve:
 * se cierra y se vuelve al login.
 */
export const jwtAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const alBackend = req.url.startsWith(environment.apiBaseUrl) && !req.url.includes('/publico/');
  const token = auth.getAccessToken();

  // Si la petición ya trae su propio Authorization (p. ej. el perfil durante el login), se respeta
  const conToken = alBackend && token && !req.headers.has('Authorization')
    ? req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) })
    : req;

  return next(conToken).pipe(
    catchError((err: HttpErrorResponse) => {
      if (alBackend && err.status === 401 && auth.estaLogueado()) {
        auth.logout('expirada');
      }
      return throwError(() => err);
    }),
  );
};
