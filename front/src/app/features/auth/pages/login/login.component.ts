import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { problemMessage } from '../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { homeUrl } from '../../../../core/auth/auth.guards';
import { LogtoAuthService } from '../../../../core/auth/logto-auth.service';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly logto = inject(LogtoAuthService);
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  /** The accounts listed in the exercise; hidden when the environment turns the hint off. */
  protected readonly demoAccounts = environment.demoAccounts;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    try {
      await this.session.signInWithPassword(email.trim(), password);
      await this.router.navigateByUrl(homeUrl(this.session));
    } catch (error: unknown) {
      const invalidCredentials = error instanceof HttpErrorResponse && error.status === HttpStatusCode.Unauthorized;
      this.error.set(invalidCredentials ? 'Correo o contraseña incorrectos.' : problemMessage(error));
      this.form.controls.password.reset();
    } finally {
      this.submitting.set(false);
    }
  }

  protected useDemoAccount(email: string): void {
    this.form.setValue({ email, password: '123' });
    this.error.set(null);
  }

  protected signInWith(provider: 'google' | 'facebook'): void {
    void this.logto.signIn(provider);
  }
}
