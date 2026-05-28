import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject }                from '@angular/core';
import { KeycloakService }       from 'keycloak-angular';
import { Router }                from '@angular/router';
import { from, switchMap, catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const kc     = inject(KeycloakService);
  const router = inject(Router);

  // Ne pas intercepter les appels vers Keycloak lui-même ni les assets
  if (
    req.url.includes('/realms/') ||
    req.url.includes('/auth/realms/') ||
    req.url.includes('/assets/')
  ) {
    return next(req);
  }

  // Pas d'API → passer tel quel
  if (!req.url.includes('/api/')) {
    return next(req);
  }

  // Si non connecté → passer sans token (endpoints publics)
  if (!kc.isLoggedIn()) {
    return next(req);
  }

  return from(kc.getToken()).pipe(
    switchMap(token => {
      const authed = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
      return next(authed);
    }),
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        // Token expiré → refresh
        return from(kc.updateToken(10)).pipe(
          switchMap(refreshed => {
            if (!refreshed) {
              kc.logout(`${window.location.origin}/landing`);
              return throwError(() => err);
            }
            return from(kc.getToken()).pipe(
              switchMap(newToken => {
                const retried = req.clone({
                  setHeaders: { Authorization: `Bearer ${newToken}` }
                });
                return next(retried);
              })
            );
          }),
          catchError(() => {
            kc.logout(`${window.location.origin}/landing`);
            return throwError(() => err);
          })
        );
      }
      if (err.status === 403) {
        router.navigate(['/discover']);
      }
      return throwError(() => err);
    })
  );
};
