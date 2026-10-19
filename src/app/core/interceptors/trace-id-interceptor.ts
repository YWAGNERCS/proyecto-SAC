import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Agrega X-Trace-ID a las peticiones al backend (no a Keycloak, que no acepta ese header por CORS). */
export const traceIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiBaseUrl)) return next(req);
  const traceId = crypto.randomUUID();
  return next(req.clone({ headers: req.headers.set('X-Trace-ID', traceId) }));
};
