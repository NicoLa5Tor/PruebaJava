import { Routes } from '@angular/router';

export const LOANS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/my-loans/my-loans.component').then((component) => component.MyLoansComponent),
  },
];
