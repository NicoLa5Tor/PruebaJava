import { TestBed } from '@angular/core/testing';
import { Route, Router, UrlSegment, UrlTree } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { homeUrl, roleGuard } from './auth.guards';
import { UserRole } from './auth.models';

describe('roleGuard', () => {
  let roles: UserRole[] | null;

  beforeEach(() => {
    roles = null;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthSessionService,
          useValue: {
            isAuthenticated: () => roles !== null,
            hasRole: (role: UserRole) => roles?.includes(role) ?? false,
            hasAnyRole: (candidates: readonly UserRole[]) => candidates.some((role) => roles?.includes(role) ?? false),
          },
        },
      ],
    });
  });

  const run = (...allowed: UserRole[]) =>
    TestBed.runInInjectionContext(() => roleGuard(...allowed)({} as Route, [] as UrlSegment[], {} as never));
  const urlOf = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('sends anonymous visitors to the login page', () => {
    expect(urlOf(run('ADMIN'))).toBe('/login');
  });

  it('lets in anyone holding one of the allowed roles', () => {
    roles = ['SUPER_ADMIN'];
    expect(run('ADMIN', 'SUPER_ADMIN')).toBe(true);
    roles = ['ADMIN'];
    expect(run('ADMIN', 'SUPER_ADMIN')).toBe(true);
  });

  it('redirects applicants away from the review queue to their own loans', () => {
    roles = ['USER'];
    expect(urlOf(run('ADMIN', 'SUPER_ADMIN'))).toBe('/loans');
  });

  it('keeps plain admins out of user management', () => {
    roles = ['ADMIN'];
    expect(urlOf(run('SUPER_ADMIN'))).toBe('/admin/loans');
  });
});

describe('homeUrl', () => {
  const sessionWith = (roles: UserRole[]) =>
    ({
      hasRole: (role: UserRole) => roles.includes(role),
      hasAnyRole: (candidates: readonly UserRole[]) => candidates.some((role) => roles.includes(role)),
    }) as AuthSessionService;

  it('picks the page that matches the main role', () => {
    expect(homeUrl(sessionWith(['USER']))).toBe('/loans');
    expect(homeUrl(sessionWith(['ADMIN']))).toBe('/admin/loans');
    expect(homeUrl(sessionWith(['SUPER_ADMIN']))).toBe('/admin/loans');
    expect(homeUrl(sessionWith([]))).toBe('/account');
  });
});
