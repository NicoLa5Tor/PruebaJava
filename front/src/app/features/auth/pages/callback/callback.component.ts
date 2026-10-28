import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { LogtoAuthService } from '../../../../core/auth/logto-auth.service';
import { PendingRegistration } from '../../data-access/registration';

@Component({
  selector: 'app-callback',
  imports: [RouterLink],
  templateUrl: './callback.component.html',
  styleUrl: './callback.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CallbackComponent {
  private readonly auth = inject(LogtoAuthService);
  private readonly router = inject(Router);
  private readonly pendingRegistration = inject(PendingRegistration);
  protected readonly error = signal<string | null>(null);

  constructor() {
    afterNextRender(() => {
      void this.completeSignIn();
    });
  }

  private async completeSignIn(): Promise<void> {
    try {
      const result = await this.auth.completeCallback(window.location.href);
      if (result?.status === 'registration-required') {
        this.pendingRegistration.set(result.profile);
        await this.router.navigateByUrl('/register', { replaceUrl: true });
        return;
      }
      await this.router.navigateByUrl(result ? '/' : '/login', { replaceUrl: true });
    } catch (error: unknown) {
      console.error('Logto callback failed', error);
      this.error.set(this.messageFor(error));
    }
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail;
      return typeof detail === 'string' ? detail : `El backend rechazó el inicio de sesión (${error.status}).`;
    }
    return error instanceof Error ? error.message : 'No fue posible completar el inicio de sesión.';
  }
}
