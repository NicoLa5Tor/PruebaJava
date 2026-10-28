import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, firstValueFrom, map, shareReplay, tap } from 'rxjs';
import { ApiClientService } from '../api/api-client.service';
import {
  AccessTokenResponse,
  CurrentUser,
  RegistrationDetails,
  RegistrationProfile,
  SignInResult,
  UserRole,
} from './auth.models';

/**
 * Holds the local session. The access token lives only in memory; the refresh token is an HttpOnly cookie
 * that JavaScript can never read, so a page reload restores the session through {@link restore}.
 */
@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly api = inject(ApiClientService);
  private readonly token = signal<string | null>(null);
  private readonly user = signal<CurrentUser | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly accessToken = this.token.asReadonly();
  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.token() !== null && this.user() !== null);

  hasRole(role: UserRole): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  /** Never creates an account: an unknown identity comes back as `registration-required`. */
  async signIn(idToken: string): Promise<SignInResult> {
    let tokens: AccessTokenResponse;
    try {
      tokens = await firstValueFrom(
        this.api.post<AccessTokenResponse>('auth/logto', { idToken }, { withCredentials: true }),
      );
    } catch (error: unknown) {
      const profile = registrationProfileFrom(error);
      if (profile) {
        return { status: 'registration-required', profile };
      }
      throw error;
    }
    await this.startSession(tokens);
    return { status: 'signed-in' };
  }

  async register(idToken: string, details: RegistrationDetails): Promise<void> {
    const tokens = await firstValueFrom(
      this.api.post<AccessTokenResponse>('auth/register', { idToken, ...details }, { withCredentials: true }),
    );
    await this.startSession(tokens);
  }

  private async startSession(tokens: AccessTokenResponse): Promise<void> {
    this.token.set(tokens.accessToken);
    await this.loadCurrentUser();
  }

  async restore(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
      await this.loadCurrentUser();
    } catch {
      this.clear();
    }
  }

  /** Concurrent callers share a single request so the rotated refresh cookie is only spent once. */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.api
      .post<AccessTokenResponse>('auth/refresh', null, { withCredentials: true })
      .pipe(
        map((tokens) => tokens.accessToken),
        tap({ next: (token) => this.token.set(token), error: () => this.clear() }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    return this.refreshInFlight;
  }

  replaceCurrentUser(user: CurrentUser): void {
    this.user.set(user);
  }

  async signOut(): Promise<void> {
    this.clear();
    await firstValueFrom(this.api.post<void>('auth/logout', null, { withCredentials: true })).catch(() => undefined);
  }

  private async loadCurrentUser(): Promise<void> {
    this.user.set(await firstValueFrom(this.api.get<CurrentUser>('users/me')));
  }

  private clear(): void {
    this.token.set(null);
    this.user.set(null);
  }
}

function registrationProfileFrom(error: unknown): RegistrationProfile | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== HttpStatusCode.NotFound) {
    return null;
  }
  const body = error.error as (Partial<RegistrationProfile> & { code?: string }) | null;
  if (body?.code !== 'REGISTRATION_REQUIRED' || !body.email) {
    return null;
  }
  return {
    email: body.email,
    firstName: body.firstName ?? null,
    lastName: body.lastName ?? null,
    displayName: body.displayName ?? null,
  };
}
