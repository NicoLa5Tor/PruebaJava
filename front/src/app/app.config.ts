import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { API_BASE_URL } from './core/api/api.config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthSessionService } from './core/auth/auth-session.service';
import { LOGTO_SETTINGS } from './core/auth/logto.config';
import { routes } from './app.routes';

registerLocaleData(localeEsCo);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'es-CO' },
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes, withComponentInputBinding()),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    { provide: LOGTO_SETTINGS, useValue: environment.logto },
    // Restores the session from the HttpOnly refresh cookie before the first navigation runs the guards.
    provideAppInitializer(() => inject(AuthSessionService).restore()),
  ],
};
