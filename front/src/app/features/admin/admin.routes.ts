import { Routes } from '@angular/router';
import { userResolver, usersPageResolver } from './users/data-access/users.resolvers';

export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  {
    path: 'users',
    resolve: { initialPage: usersPageResolver },
    loadComponent: () =>
      import('./users/pages/user-list/user-list.component').then((component) => component.UserListComponent),
  },
  {
    path: 'users/new',
    loadComponent: () =>
      import('./users/pages/user-form/user-form.component').then((component) => component.UserFormComponent),
  },
  {
    path: 'users/:id',
    resolve: { resolvedUser: userResolver },
    loadComponent: () =>
      import('./users/pages/user-form/user-form.component').then((component) => component.UserFormComponent),
  },
];
