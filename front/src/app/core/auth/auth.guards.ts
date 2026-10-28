import { inject } from '@angular/core';
import { CanMatchFn, Router, UrlTree } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { REVIEWER_ROLES, UserRole } from './auth.models';

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

/**
 * Lets the route match when the user has any of the given roles; otherwise sends them to their own home page.
 * UI convenience only: the backend enforces the same roles with @PreAuthorize.
 */
export function roleGuard(...roles: UserRole[]): CanMatchFn {
  return () => {
    const session = inject(AuthSessionService);
    const router = inject(Router);
    if (!session.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return session.hasAnyRole(roles) || homeFor(session, router);
  };
}

/** Reviewers land on the review queue, applicants on their loans, anyone else on their account. */
export function homeUrl(session: AuthSessionService): string {
  if (session.hasAnyRole(REVIEWER_ROLES)) {
    return '/admin/loans';
  }
  return session.hasRole('USER') ? '/loans' : '/account';
}

function homeFor(session: AuthSessionService, router: Router): UrlTree {
  return router.parseUrl(homeUrl(session));
}
