import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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
  protected readonly user = inject(AuthSessionService).currentUser;
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
}
