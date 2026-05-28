import {
  ApplicationConfig, APP_INITIALIZER, importProvidersFrom
} from '@angular/core';
import { provideRouter }         from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations }     from '@angular/platform-browser/animations';
import { KeycloakService }       from 'keycloak-angular';
import { KeycloakAngularModule } from 'keycloak-angular';
import { routes }                from './app.routes';
import { authInterceptor }       from './core/interceptors/auth.interceptor';
import { AuthService }           from './services/auth.service';
import { AlertService }          from './services/alert.service';
import { StrategyService }       from './services/strategy.service';
import { WatchlistService }      from './services/watchlist.service';
import { SseService }            from './services/sse.service';
import { ThemeService }          from './services/theme.service';
import { environment }           from '../environments/environment';

function initializeApp(
  keycloak:    KeycloakService,
  auth:        AuthService,
  alertSvc:    AlertService,
  strategySvc: StrategyService,
  watchlistSvc:WatchlistService,
  sse:         SseService,
  theme:       ThemeService   // instancié ici = effect() s'enregistre dès le départ
) {
  return async () => {
    // 1. Init Keycloak
    await keycloak.init({
      config: {
        url:      environment.keycloak.url,
        realm:    environment.keycloak.realm,
        clientId: environment.keycloak.clientId,
      },
      initOptions: {
        onLoad:                  'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/assets/silent-check-sso.html`,
        checkLoginIframe:        false,
        pkceMethod:              'S256',
      },
      enableBearerInterceptor: false,
      bearerExcludedUrls: ['/assets'],
    });

    // 2. Si connecté → charger toutes les données
    const loggedIn = await keycloak.isLoggedIn();
    if (loggedIn) {
      await auth.init();
      alertSvc.load();
      strategySvc.load();
      watchlistSvc.load();   // ← charge la watchlist indépendamment

      const profile = auth.profile();
      if (profile?.id) {
        sse.connect(String(profile.id));
      }
    }
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptors([authInterceptor])),
    importProvidersFrom(KeycloakAngularModule),
    KeycloakService,

    {
      provide:    APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [
        KeycloakService, AuthService, AlertService,
        StrategyService, WatchlistService, SseService, ThemeService
      ],
      multi: true,
    },
  ],
};
