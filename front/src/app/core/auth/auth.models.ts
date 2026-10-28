export type UserRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN';
export type UserStatus = 'ACTIVE' | 'INACTIVE';

export const ROLE_LABELS: Record<UserRole, string> = {
  USER: 'Usuario',
  ADMIN: 'Administrador',
  SUPER_ADMIN: 'Super administrador',
};
/** Roles that may approve or reject loans. */
export const REVIEWER_ROLES: readonly UserRole[] = ['ADMIN', 'SUPER_ADMIN'];
/** Most privileged first; used to show a single role per person. */
const ROLE_PRIORITY: readonly UserRole[] = ['SUPER_ADMIN', 'ADMIN', 'USER'];
export const STATUS_LABELS: Record<UserStatus, string> = { ACTIVE: 'Activo', INACTIVE: 'Inactivo' };

/** Mirrors the backend UserResponse. */
export interface UserAccount {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  logtoSubject: string | null;
  emailVerified: boolean;
  /** True when the account can sign in with email and password (not only through Logto). */
  hasPassword: boolean;
  status: UserStatus;
  roles: UserRole[];
  createdAt: string;
  updatedAt: string;
}

export type CurrentUser = UserAccount;

/** The refresh token is never part of this payload: the backend keeps it in an HttpOnly cookie. */
export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

/** What Logto already knows about someone who has no local account yet; used to prefill the registration form. */
export interface RegistrationProfile {
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
}

export interface RegistrationDetails {
  firstName: string;
  lastName: string;
  displayName: string;
}

export type SignInResult =
  | { status: 'signed-in' }
  | { status: 'registration-required'; profile: RegistrationProfile };

export function displayNameOf(user: Pick<UserAccount, 'displayName' | 'firstName' | 'lastName' | 'email'>): string {
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ');
  return user.displayName || fullName || user.email;
}

export function primaryRole(roles: readonly UserRole[]): UserRole {
  return ROLE_PRIORITY.find((role) => roles.includes(role)) ?? 'USER';
}
