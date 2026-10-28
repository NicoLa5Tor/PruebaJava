import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { REVIEWER_ROLES, ROLE_LABELS, displayNameOf, primaryRole } from '../../auth/auth.models';
import { LogtoAuthService } from '../../auth/logto-auth.service';
import { UserAvatarComponent } from '../../../shared/ui/user-avatar/user-avatar.component';

/** Authenticated layout: top navbar plus the routed page. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UserAvatarComponent],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly auth = inject(LogtoAuthService);
  private readonly session = inject(AuthSessionService);
  protected readonly user = this.session.currentUser;
  protected readonly isApplicant = computed(() => this.user() !== null && this.session.hasRole('USER'));
  protected readonly isReviewer = computed(() => this.user() !== null && this.session.hasAnyRole(REVIEWER_ROLES));
  protected readonly isSuperAdmin = computed(() => this.user() !== null && this.session.hasRole('SUPER_ADMIN'));
  protected readonly userName = computed(() => {
    const user = this.user();
    return user ? displayNameOf(user) : '';
  });
  protected readonly roleLabel = computed(() => ROLE_LABELS[primaryRole(this.user()?.roles ?? [])]);
  /** True while the router resolves data for the next page. */
  protected readonly navigating = toSignal(
    inject(Router).events.pipe(
      filter(
        (event) =>
          event instanceof NavigationStart ||
          event instanceof NavigationEnd ||
          event instanceof NavigationCancel ||
          event instanceof NavigationError,
      ),
      map((event) => event instanceof NavigationStart),
    ),
    { initialValue: false },
  );
  protected readonly menuOpen = signal(false);
  protected readonly signingOut = signal(false);

  protected signOut(): void {
    this.signingOut.set(true);
    void this.auth.signOut();
  }
}
