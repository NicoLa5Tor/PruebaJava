import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthSessionService } from './core/auth/auth-session.service';
import { authGuard, roleGuard } from './core/auth/auth.guards';
import { ShellComponent } from './core/layout/shell/shell.component';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => (inject(AuthSessionService).hasRole('ADMIN') ? '/admin/users' : '/account'),
  },
  {
    path: '',
    loadChildren: () => import('./features/auth/auth.routes').then((module) => module.AUTH_ROUTES),
  },
  {
    path: '',
    component: ShellComponent,
    canMatch: [authGuard],
    children: [
      {
        path: 'account',
        loadChildren: () => import('./features/account/account.routes').then((module) => module.ACCOUNT_ROUTES),
      },
      {
        path: 'admin',
        canMatch: [roleGuard('ADMIN')],
        loadChildren: () => import('./features/admin/admin.routes').then((module) => module.ADMIN_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
