import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/**
 * Adjunta el header Authorization: Basic <token> a TODAS las peticiones
 * dirigidas a /api/v1/admin/**.
 * Las rutas públicas (/api/v1/publico/**) no llevan credenciales.
 */
export const basicAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getBasicToken();

  // Solo rutas de admin necesitan credenciales
  const esRutaAdmin = req.url.includes('/admin/');

  if (token && esRutaAdmin) {
    const cloned = req.clone({
      headers: req.headers.set('Authorization', `Basic ${token}`),
    });
    return next(cloned);
  }

  return next(req);
};
