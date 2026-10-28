import { Routes } from '@angular/router';
import { guestGuard } from '../../core/auth/auth.guards';
import { registrationProfileResolver } from './data-access/registration';

export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    canMatch: [guestGuard],
    loadComponent: () => import('./pages/login/login.component').then((component) => component.LoginComponent),
  },
  {
    path: 'callback',
    loadComponent: () =>
      import('./pages/callback/callback.component').then((component) => component.CallbackComponent),
  },
  {
    path: 'register',
    canMatch: [guestGuard],
    resolve: { profile: registrationProfileResolver },
    loadComponent: () =>
      import('./pages/register/register.component').then((component) => component.RegisterComponent),
  },
];
