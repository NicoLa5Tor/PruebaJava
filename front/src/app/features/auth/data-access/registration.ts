import { Injectable, inject } from '@angular/core';
import { RedirectCommand, ResolveFn, Router } from '@angular/router';
import { RegistrationProfile } from '../../../core/auth/auth.models';
import { LogtoAuthService } from '../../../core/auth/logto-auth.service';

/** Hands the profile from the callback to the registration page without an extra request. */
@Injectable({ providedIn: 'root' })
export class PendingRegistration {
  private profile: RegistrationProfile | null = null;

  set(profile: RegistrationProfile): void {
    this.profile = profile;
  }

  take(): RegistrationProfile | null {
    const profile = this.profile;
    this.profile = null;
    return profile;
  }
}

/**
 * Provides the Logto profile to /register. After a reload it asks the backend again with Logto's stored ID token;
 * anyone who already has an account goes home and anyone without a Logto session goes back to /login.
 */
export const registrationProfileResolver: ResolveFn<RegistrationProfile> = async () => {
  const pending = inject(PendingRegistration).take();
  const logto = inject(LogtoAuthService);
  const router = inject(Router);
  if (pending) {
    return pending;
  }
  try {
    const result = await logto.resumeSignIn();
    if (result?.status === 'registration-required') {
      return result.profile;
    }
    return new RedirectCommand(router.parseUrl(result ? '/' : '/login'));
  } catch {
    return new RedirectCommand(router.parseUrl('/login'));
  }
};
