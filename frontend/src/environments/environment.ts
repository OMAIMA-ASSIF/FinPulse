// src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api',
  keycloak: {
    url:      'http://localhost:8080',
    realm:    'finpulse',
    clientId: 'finpulse-frontend'
  }
};
