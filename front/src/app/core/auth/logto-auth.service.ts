import { Injectable, inject } from '@angular/core';
import LogtoClient, { UserScope } from '@logto/browser';
import { AuthSessionService } from './auth-session.service';
import { RegistrationDetails, SignInResult } from './auth.models';
import { LOGTO_SETTINGS } from './logto.config';

type SocialProvider = 'google' | 'facebook';

@Injectable({ providedIn: 'root' })
export class LogtoAuthService {
  private readonly session = inject(AuthSessionService);
  private readonly client = new LogtoClient({ ...inject(LOGTO_SETTINGS), scopes: [UserScope.Email] });

  async signIn(provider?: SocialProvider): Promise<void> {
    await this.client.signIn({
      redirectUri: `${window.location.origin}/callback`,
      ...(provider ? { directSignIn: { method: 'social' as const, target: provider } } : {}),
    });
  }

  /** Returns null when the URL is not a Logto redirect. */
  async completeCallback(callbackUri: string): Promise<SignInResult | null> {
    if (!(await this.client.isSignInRedirected(callbackUri))) {
      return null;
    }
    await this.client.handleSignInCallback(callbackUri);
    return this.resumeSignIn();
  }

  /**
   * Retries the local sign-in with the ID token Logto keeps in its own storage, so a reload of the
   * registration page still knows who is registering. Returns null when there is no Logto session.
   */
  async resumeSignIn(): Promise<SignInResult | null> {
    const idToken = await this.client.getIdToken();
    return idToken ? this.session.signIn(idToken) : null;
  }

  async register(details: RegistrationDetails): Promise<void> {
    const idToken = await this.client.getIdToken();
    if (!idToken) {
      throw new Error('Tu sesión de Logto expiró. Vuelve a iniciar sesión.');
    }
    await this.session.register(idToken, details);
  }

  /** Leaves Logto too, so the person can choose a different account on the next attempt. */
  async cancelRegistration(): Promise<void> {
    await this.client.signOut(`${window.location.origin}/login`);
  }

  async signOut(): Promise<void> {
    await this.session.signOut();
    await this.client.signOut(`${window.location.origin}/login`);
  }
}
