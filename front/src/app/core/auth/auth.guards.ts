import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { UserRole } from './auth.models';

// canMatch (instead of canActivate) keeps the lazy chunks of protected features from being downloaded at all.

export const authGuard: CanMatchFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  return session.isAuthenticated() || router.createUrlTree(['/login']);
};

export const guestGuard: CanMatchFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  return !session.isAuthenticated() || router.createUrlTree(['/']);
};

/** UI convenience only: the backend enforces the same role with @PreAuthorize. */
export function roleGuard(role: UserRole): CanMatchFn {
  return () => {
    const session = inject(AuthSessionService);
    const router = inject(Router);
    if (!session.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return session.hasRole(role) || router.createUrlTree(['/account']);
  };
}
