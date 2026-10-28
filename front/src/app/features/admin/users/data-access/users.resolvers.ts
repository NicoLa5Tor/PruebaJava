import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { catchError, of } from 'rxjs';
import { PageResponse } from '../../../../core/api/page-response';
import { UserAccount } from '../../../../core/auth/auth.models';
import { UsersApiService } from './users-api.service';

export const USERS_PAGE_SIZE = 10;

// Resolvers fetch during navigation so the page appears with its data already in place.
// On failure they resolve to null instead of cancelling the navigation; the page then loads and reports the error.

export const usersPageResolver: ResolveFn<PageResponse<UserAccount> | null> = () =>
  inject(UsersApiService)
    .findAll({ page: 0, size: USERS_PAGE_SIZE, search: '' })
    .pipe(catchError(() => of(null)));

export const userResolver: ResolveFn<UserAccount | null> = (route) =>
  inject(UsersApiService)
    .findById(route.paramMap.get('id') ?? '')
    .pipe(catchError(() => of(null)));
