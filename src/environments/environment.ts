export const environment = {
  apiBaseUrl: 'http://localhost:8080/api',
  /** Proveedor de identidad (S10b): emite los tokens que valida el backend. */
  keycloak: {
    url: 'http://localhost:38080',
    realm: 'bomerp',
    clientId: 'bomerp-frontend',
  },
};
