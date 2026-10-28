import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiClientService } from '../../../core/api/api-client.service';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { UserAccount } from '../../../core/auth/auth.models';

export interface UpdateProfilePayload {
  firstName: string;
  lastName: string;
  displayName: string;
  avatarUrl: string;
}

@Injectable({ providedIn: 'root' })
export class AccountApiService {
  private readonly api = inject(ApiClientService);
  private readonly session = inject(AuthSessionService);

  updateProfile(payload: UpdateProfilePayload): Observable<UserAccount> {
    return this.api
      .put<UserAccount>('users/me', payload)
      .pipe(tap((user) => this.session.replaceCurrentUser(user)));
  }
}
