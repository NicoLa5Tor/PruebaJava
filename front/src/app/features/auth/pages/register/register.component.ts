import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { problemMessage } from '../../../../core/api/problem-message';
import { RegistrationProfile } from '../../../../core/auth/auth.models';
import { LogtoAuthService } from '../../../../core/auth/logto-auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule],
  templateUrl: './register.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent implements OnInit {
  /** Provided by registrationProfileResolver. */
  readonly profile = input.required<RegistrationProfile>();

  private readonly auth = inject(LogtoAuthService);
  private readonly router = inject(Router);
  protected readonly saving = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', Validators.maxLength(100)],
    displayName: ['', Validators.maxLength(150)],
  });

  ngOnInit(): void {
    const { firstName, lastName, displayName } = this.profile();
    this.form.patchValue({
      firstName: firstName ?? '',
      lastName: lastName ?? '',
      displayName: displayName ?? '',
    });
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.auth.register(this.form.getRawValue());
      await this.router.navigateByUrl('/', { replaceUrl: true });
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Conflict) {
        // The account already exists (double submit, another tab): just sign in.
        const result = await this.auth.resumeSignIn().catch(() => null);
        if (result?.status === 'signed-in') {
          await this.router.navigateByUrl('/', { replaceUrl: true });
          return;
        }
      }
      this.saving.set(false);
      this.error.set(error instanceof Error ? error.message : problemMessage(error));
    }
  }

  protected cancel(): void {
    this.cancelling.set(true);
    void this.auth.cancelRegistration();
  }
}
