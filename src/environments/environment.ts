export const environment = {
  apiBaseUrl: 'http://localhost:8080/api',
  /**
   * Proveedor de identidad (S10b/S11). La SPA inicia sesión con Authorization Code + PKCE:
   * redirige a la pantalla de Keycloak y vuelve a /auth/callback con un código de un solo uso.
   */
  keycloak: {
    issuer: 'http://localhost:38080/realms/bomerp',
    clientId: 'bomerp-frontend',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200/',
  },
};
