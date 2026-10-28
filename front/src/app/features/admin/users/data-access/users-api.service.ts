import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../../core/api/api-client.service';
import { PageResponse } from '../../../../core/api/page-response';
import { UserAccount, UserRole, UserStatus } from '../../../../core/auth/auth.models';

export interface UserQuery {
  page: number;
  size: number;
  search: string;
}

export interface CreateUserPayload {
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  avatarUrl: string;
  roles: UserRole[];
  /** Optional: lets the person sign in with email and password besides Logto. */
  password?: string;
}

/** Every field is optional: omitted fields are left unchanged by the backend. */
export interface UpdateUserPayload extends Partial<CreateUserPayload> {
  status?: UserStatus;
  emailVerified?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly api = inject(ApiClientService);

  findAll({ page, size, search }: UserQuery): Observable<PageResponse<UserAccount>> {
    return this.api.get<PageResponse<UserAccount>>('users', {
      params: { page, size, ...(search ? { search } : {}) },
    });
  }

  findById(id: string): Observable<UserAccount> {
    return this.api.get<UserAccount>(`users/${id}`);
  }

  create(payload: CreateUserPayload): Observable<UserAccount> {
    return this.api.post<UserAccount>('users', payload);
  }

  update(id: string, payload: UpdateUserPayload): Observable<UserAccount> {
    return this.api.put<UserAccount>(`users/${id}`, payload);
  }

  changePassword(id: string, password: string): Observable<void> {
    return this.api.put<void>(`users/${id}/password`, { password });
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`users/${id}`);
  }
}
