import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LogtoAuthService } from '../../../../core/auth/logto-auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly auth = inject(LogtoAuthService);

  protected signInWith(provider: 'google' | 'facebook'): void {
    void this.auth.signIn(provider);
  }
}
