import { Routes } from '@angular/router';
import { roleGuard } from '../../core/auth/auth.guards';
import { userResolver, usersPageResolver } from './users/data-access/users.resolvers';

export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'loans' },
  {
    // ADMIN and SUPER_ADMIN (checked by the parent route). The page lives with the rest of the loans feature.
    path: 'loans',
    loadComponent: () =>
      import('../loans/pages/loan-review/loan-review.component').then((component) => component.LoanReviewComponent),
  },
  {
    path: 'users',
    canMatch: [roleGuard('SUPER_ADMIN')],
    children: [
      {
        path: '',
        resolve: { initialPage: usersPageResolver },
        loadComponent: () =>
          import('./users/pages/user-list/user-list.component').then((component) => component.UserListComponent),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./users/pages/user-form/user-form.component').then((component) => component.UserFormComponent),
      },
      {
        path: ':id',
        resolve: { resolvedUser: userResolver },
        loadComponent: () =>
          import('./users/pages/user-form/user-form.component').then((component) => component.UserFormComponent),
      },
    ],
  },
];
