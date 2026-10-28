import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map, of, switchMap } from 'rxjs';
import { problemMessage } from '../../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../../core/auth/auth-session.service';
import { ROLE_LABELS, UserAccount, UserRole, UserStatus, displayNameOf, primaryRole } from '../../../../../core/auth/auth.models';
import { UserAvatarComponent } from '../../../../../shared/ui/user-avatar/user-avatar.component';
import { UsersApiService } from '../../data-access/users-api.service';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, UserAvatarComponent],
  templateUrl: './user-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormComponent implements OnInit {
  /** Bound from the `users/:id` route; undefined on `users/new`. */
  readonly id = input<string>();
  /** Preloaded by userResolver; null when the resolver failed, so the page fetches again to show the error. */
  readonly resolvedUser = input<UserAccount | null>(null);

  private readonly api = inject(UsersApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(AuthSessionService);

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly isSelf = computed(() => this.id() === this.session.currentUser()?.id);
  protected readonly user = signal<UserAccount | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly displayNameOf = displayNameOf;
  protected readonly roleOptions: { value: UserRole; label: string; hint: string }[] = [
    { value: 'USER', label: ROLE_LABELS.USER, hint: 'Solicita préstamos y consulta su estado.' },
    { value: 'ADMIN', label: ROLE_LABELS.ADMIN, hint: 'Aprueba o rechaza solicitudes de préstamo.' },
    { value: 'SUPER_ADMIN', label: ROLE_LABELS.SUPER_ADMIN, hint: 'Revisa préstamos y administra usuarios y roles.' },
  ];

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    firstName: ['', Validators.maxLength(100)],
    lastName: ['', Validators.maxLength(100)],
    displayName: ['', Validators.maxLength(150)],
    avatarUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^(https?:\/\/\S+)?$/)]],
    role: ['USER' as UserRole, Validators.required],
    // Optional on create (Logto-only accounts have none); on edit, filling it resets the password.
    password: ['', [Validators.minLength(8), Validators.maxLength(72)]],
    status: ['ACTIVE' as UserStatus],
    emailVerified: [false],
  });

  ngOnInit(): void {
    const id = this.id();
    if (!id) {
      return;
    }
    if (this.isSelf()) {
      // Mirrors the backend rule: a super admin cannot demote or deactivate themselves.
      this.form.controls.role.disable();
      this.form.controls.status.disable();
    }
    const resolved = this.resolvedUser();
    if (resolved) {
      this.fill(resolved);
      return;
    }
    this.loading.set(true);
    this.api
      .findById(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.fill(user);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.error.set(problemMessage(error, 'No fue posible cargar el usuario.'));
        },
      });
  }

  private fill(user: UserAccount): void {
    this.user.set(user);
    this.form.patchValue({
      email: user.email,
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      displayName: user.displayName ?? '',
      avatarUrl: user.avatarUrl ?? '',
      role: primaryRole(user.roles),
      status: user.status,
      emailVerified: user.emailVerified,
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { role, password, status, emailVerified, ...profile } = this.form.getRawValue();
    const roles: UserRole[] = [role];
    const id = this.id();
    const request = id
      ? this.api.update(id, { ...profile, roles, status, emailVerified }).pipe(
          // A new password, if typed, is saved after the profile so both changes land before leaving the page.
          switchMap((saved) => (password ? this.api.changePassword(id, password).pipe(map(() => saved)) : of(saved))),
        )
      : this.api.create({ ...profile, roles, ...(password ? { password } : {}) });

    this.saving.set(true);
    this.error.set(null);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        if (saved.id === this.session.currentUser()?.id) {
          this.session.replaceCurrentUser(saved);
        }
        void this.router.navigateByUrl('/admin/users');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
