export type UserRole = 'USER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'INACTIVE';

export const ROLE_LABELS: Record<UserRole, string> = { USER: 'Usuario', ADMIN: 'Administrador' };
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
