import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { isPlatformBrowser } from '@angular/common';
import {
  ApplicationConfig,
  LOCALE_ID,
  PLATFORM_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { API_BASE_URL } from './core/api/api.config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthSessionService } from './core/auth/auth-session.service';
import { LOGTO_SETTINGS } from './core/auth/logto.config';
import { RuntimeConfigService } from './core/config/runtime-config.service';
import { routes } from './app.routes';

registerLocaleData(localeEsCo);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'es-CO' },
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideRouter(routes, withComponentInputBinding()),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    { provide: LOGTO_SETTINGS, useValue: environment.logto },
    // In the browser, restores the session from the HttpOnly refresh cookie before the first navigation runs the
    // guards. The server never holds a session: it only renders the public login page.
    provideAppInitializer(() => {
      if (!isPlatformBrowser(inject(PLATFORM_ID))) {
        return;
      }
      const session = inject(AuthSessionService);
      return Promise.all([session.restore(), inject(RuntimeConfigService).load()]).then(() => undefined);
    }),
    provideClientHydration(withEventReplay()),
  ],
};
