import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthSessionService } from './core/auth/auth-session.service';
import { authGuard, homeUrl, roleGuard } from './core/auth/auth.guards';
import { REVIEWER_ROLES } from './core/auth/auth.models';
import { ShellComponent } from './core/layout/shell/shell.component';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => homeUrl(inject(AuthSessionService)),
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
        path: 'loans',
        canMatch: [roleGuard('USER')],
        loadChildren: () => import('./features/loans/loans.routes').then((module) => module.LOANS_ROUTES),
      },
      {
        path: 'account',
        loadChildren: () => import('./features/account/account.routes').then((module) => module.ACCOUNT_ROUTES),
      },
      {
        path: 'admin',
        canMatch: [roleGuard(...REVIEWER_ROLES)],
        loadChildren: () => import('./features/admin/admin.routes').then((module) => module.ADMIN_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
