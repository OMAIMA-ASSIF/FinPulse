import { inject }                    from '@angular/core';
import { CanActivateFn, Router }     from '@angular/router';
import { KeycloakService }           from 'keycloak-angular';

/**
 * Protège les routes authentifiées.
 * Non connecté → /landing
 */
export const authGuard: CanActivateFn = async (_route, _state) => {
  const kc     = inject(KeycloakService);
  const router = inject(Router);

  const loggedIn = await kc.isLoggedIn();
  if (loggedIn) return true;

  router.navigate(['/landing']);
  return false;
};

/**
 * Routes publiques (landing, register).
 * Déjà connecté → /discover
 */
export const publicGuard: CanActivateFn = async (_route, _state) => {
  const kc     = inject(KeycloakService);
  const router = inject(Router);

  const loggedIn = await kc.isLoggedIn();
  if (loggedIn) {
    router.navigate(['/discover']);
    return false;
  }
  return true;
};
