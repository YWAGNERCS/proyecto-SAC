/** Claims del access token de Keycloak que usa la SPA. */
export interface JwtPayload {
  sub: string;
  exp: number;
  name?: string;
  email?: string;
  preferred_username?: string;
  realm_access?: { roles: string[] };
}

/**
 * Lee los claims de un JWT SIN verificar la firma: la SPA no valida tokens (eso lo hace el backend
 * con las claves públicas de Keycloak); solo los lee para decidir qué mostrar.
 */
export function decodeJwtPayload(token: string): JwtPayload | null {
  const partes = token.split('.');
  if (partes.length !== 3) return null;
  try {
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}
