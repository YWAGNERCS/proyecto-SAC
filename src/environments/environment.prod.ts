/**
 * Build de producción (S13): `ng build` usa este archivo en vez de environment.ts
 * (fileReplacements de la configuración production en angular.json).
 * Apunta al entorno de producción LOCAL (compose-prod.yml): backend 48088, Keycloak 48080 y el
 * build servido por nginx en 4300. Antes de un despliegue real, cambiar por el dominio de JD.
 * Aquí no va ningún secreto: el SPA es un cliente público (PKCE), todo esto lo ve el navegador.
 */
export const environment = {
  apiBaseUrl: 'http://localhost:48088/api',
  keycloak: {
    issuer: 'http://localhost:48080/realms/bomerp',
    clientId: 'bomerp-frontend',
    redirectUri: 'http://localhost:4300/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4300/',
  },
};
