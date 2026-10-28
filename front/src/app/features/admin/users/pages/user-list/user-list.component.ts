import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, skip, switchMap, tap } from 'rxjs';
import { PageResponse } from '../../../../../core/api/page-response';
import { problemMessage } from '../../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../../core/auth/auth-session.service';
import { ROLE_LABELS, STATUS_LABELS, UserAccount, displayNameOf } from '../../../../../core/auth/auth.models';
import { UserAvatarComponent } from '../../../../../shared/ui/user-avatar/user-avatar.component';
import { UsersApiService } from '../../data-access/users-api.service';
import { USERS_PAGE_SIZE } from '../../data-access/users.resolvers';

@Component({
  selector: 'app-user-list',
  imports: [RouterLink, DatePipe, UserAvatarComponent],
  templateUrl: './user-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent {
  private readonly api = inject(UsersApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(AuthSessionService);
  private readonly searchInput = new Subject<string>();

  protected readonly currentUserId = computed(() => this.session.currentUser()?.id);
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly displayNameOf = displayNameOf;
  protected readonly placeholderRows = Array.from({ length: 5 }, (_, index) => index);

  protected readonly search = signal('');
  protected readonly pageIndex = signal(0);
  private readonly reloadCount = signal(0);
  protected readonly page = signal<PageResponse<UserAccount> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly pendingDelete = signal<UserAccount | null>(null);

  private readonly query = computed(() => ({
    page: this.pageIndex(),
    size: USERS_PAGE_SIZE,
    search: this.search(),
    reload: this.reloadCount(),
  }));

  constructor() {
    const initialPage = inject(ActivatedRoute).snapshot.data['initialPage'] as PageResponse<UserAccount> | null;
    if (initialPage) {
      this.page.set(initialPage);
      this.loading.set(false);
    }

    this.searchInput
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => {
        this.pageIndex.set(0);
        this.search.set(term);
      });

    toObservable(this.query)
      .pipe(
        // The resolver already delivered the first page; only refetch when the query changes.
        skip(initialPage ? 1 : 0),
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.api.findAll(query).pipe(
            catchError((error: unknown) => {
              this.error.set(problemMessage(error, 'No fue posible cargar los usuarios.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.page.set(page);
        }
      });
  }

  protected onSearch(term: string): void {
    this.searchInput.next(term.trim());
  }

  protected goToPage(index: number): void {
    this.pageIndex.set(index);
  }

  protected toggleStatus(user: UserAccount): void {
    const status = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.busyId.set(user.id);
    this.notice.set(null);
    this.error.set(null);
    this.api
      .update(user.id, { status })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.busyId.set(null);
          this.replaceRow(updated);
          this.notice.set(`${displayNameOf(updated)} ahora está ${STATUS_LABELS[updated.status].toLowerCase()}.`);
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          this.error.set(problemMessage(error));
        },
      });
  }

  protected confirmDelete(): void {
    const user = this.pendingDelete();
    if (!user) {
      return;
    }
    this.busyId.set(user.id);
    this.api
      .delete(user.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyId.set(null);
          this.pendingDelete.set(null);
          this.notice.set(`Se eliminó a ${displayNameOf(user)}.`);
          const isLastRowOnPage = this.page()?.content.length === 1 && this.pageIndex() > 0;
          if (isLastRowOnPage) {
            this.pageIndex.update((index) => index - 1);
          } else {
            this.reloadCount.update((count) => count + 1);
          }
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          this.pendingDelete.set(null);
          this.error.set(problemMessage(error));
        },
      });
  }

  private replaceRow(updated: UserAccount): void {
    this.page.update((page) =>
      page ? { ...page, content: page.content.map((user) => (user.id === updated.id ? updated : user)) } : page,
    );
  }
}
