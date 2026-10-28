import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { ROLE_LABELS, STATUS_LABELS, displayNameOf } from '../../../../core/auth/auth.models';
import { UserAvatarComponent } from '../../../../shared/ui/user-avatar/user-avatar.component';
import { AccountApiService } from '../../data-access/account-api.service';

@Component({
  selector: 'app-account',
  imports: [ReactiveFormsModule, DatePipe, UserAvatarComponent],
  templateUrl: './account.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {
  private readonly accountApi = inject(AccountApiService);
  private readonly session = inject(AuthSessionService);
  protected readonly user = this.session.currentUser;
  protected readonly copying = signal(false);
  protected readonly tokenCopied = signal(false);
  protected readonly tokenError = signal<string | null>(null);
  protected readonly userName = computed(() => {
    const user = this.user();
    return user ? displayNameOf(user) : '';
  });
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', Validators.maxLength(100)],
    lastName: ['', Validators.maxLength(100)],
    displayName: ['', Validators.maxLength(150)],
    avatarUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^(https?:\/\/\S+)?$/)]],
  });

  constructor() {
    this.resetForm();
  }

  protected resetForm(): void {
    const user = this.user();
    this.form.reset({
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      displayName: user?.displayName ?? '',
      avatarUrl: user?.avatarUrl ?? '',
    });
    this.saved.set(false);
    this.error.set(null);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    this.accountApi.updateProfile(this.form.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
        this.form.markAsPristine();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }

  /** Refreshes first so the copied token has its full 15 minutes; it is never stored anywhere else. */
  protected async copyToken(): Promise<void> {
    this.copying.set(true);
    this.tokenError.set(null);
    try {
      const token = await firstValueFrom(this.session.refresh());
      await navigator.clipboard.writeText(token);
      this.tokenCopied.set(true);
      setTimeout(() => this.tokenCopied.set(false), 3_000);
    } catch {
      this.tokenError.set('No fue posible copiar el token. Vuelve a iniciar sesión e inténtalo de nuevo.');
    } finally {
      this.copying.set(false);
    }
  }
}
