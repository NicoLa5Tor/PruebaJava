import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';
import { PageResponse } from '../../../../core/api/page-response';
import { problemMessage } from '../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { displayNameOf } from '../../../../core/auth/auth.models';
import { LOAN_LIMITS, LOAN_STATUS_BADGES, LOAN_STATUS_LABELS, Loan, LoanStatus } from '../../data-access/loan.models';
import { calculateOffer, monthlyRateFromEffectiveAnnual } from '../../data-access/loan-offer';
import { LoansApiService } from '../../data-access/loans-api.service';

const PAGE_SIZE = 10;

interface StatusTab {
  status: LoanStatus | null;
  label: string;
}

/** Review queue for ADMIN and SUPER_ADMIN: filter by status, approve or reject pending requests. */
@Component({
  selector: 'app-loan-review',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './loan-review.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoanReviewComponent {
  private readonly api = inject(LoansApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(AuthSessionService);

  protected readonly tabs: StatusTab[] = [
    { status: 'PENDING', label: 'Pendientes' },
    { status: 'APPROVED', label: 'Aprobados' },
    { status: 'REJECTED', label: 'Rechazados' },
    { status: 'CANCELLED', label: 'Cancelados' },
    { status: null, label: 'Todos' },
  ];
  protected readonly statusLabels = LOAN_STATUS_LABELS;
  protected readonly statusBadges = LOAN_STATUS_BADGES;
  protected readonly maxCommentLength = LOAN_LIMITS.maxCommentLength;
  protected readonly placeholderRows = [0, 1, 2];
  protected readonly reviewerName = computed(() => {
    const user = this.session.currentUser();
    return user ? displayNameOf(user) : '';
  });

  protected readonly status = signal<LoanStatus | null>('PENDING');
  protected readonly pageIndex = signal(0);
  private readonly reloadCount = signal(0);
  protected readonly page = signal<PageResponse<Loan> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly rejecting = signal<Loan | null>(null);
  protected readonly rejectForm = inject(NonNullableFormBuilder).group({
    comment: ['', Validators.maxLength(LOAN_LIMITS.maxCommentLength)],
  });
  protected readonly approving = signal<Loan | null>(null);
  protected readonly approveForm = inject(NonNullableFormBuilder).group({
    annualRate: [
      24 as number | null,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(LOAN_LIMITS.maxAnnualRate),
        Validators.pattern(/^\d+(\.\d{1,2})?$/),
      ],
    ],
    message: ['', Validators.maxLength(LOAN_LIMITS.maxCommentLength)],
  });
  private readonly approveRate = toSignal(this.approveForm.controls.annualRate.valueChanges, {
    initialValue: this.approveForm.controls.annualRate.value,
  });
  /** What the applicant will be offered; the server recomputes the same figures on approval. */
  protected readonly offerPreview = computed(() => {
    const loan = this.approving();
    const rate = this.approveRate();
    if (!loan || rate === null || this.approveForm.controls.annualRate.invalid) {
      return null;
    }
    return {
      ...calculateOffer(loan.amount, loan.termMonths, Number(rate)),
      monthlyRatePercent: monthlyRateFromEffectiveAnnual(Number(rate)) * 100,
    };
  });

  private readonly query = computed(() => ({
    status: this.status(),
    page: this.pageIndex(),
    size: PAGE_SIZE,
    reload: this.reloadCount(),
  }));

  constructor() {
    toObservable(this.query)
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.api.search(query).pipe(
            catchError((error: unknown) => {
              this.error.set(problemMessage(error, 'No fue posible cargar las solicitudes.'));
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

  protected selectTab(status: LoanStatus | null): void {
    this.status.set(status);
    this.pageIndex.set(0);
  }

  protected goToPage(index: number): void {
    this.pageIndex.set(index);
  }

  protected applicantOf(loan: Loan): string {
    return loan.applicantName ? `${loan.applicantName} · ${loan.applicantEmail}` : (loan.applicantEmail ?? '');
  }

  protected openApprove(loan: Loan): void {
    this.approveForm.reset({ annualRate: 24, message: '' });
    this.approving.set(loan);
  }

  protected confirmApprove(): void {
    const loan = this.approving();
    if (!loan || this.approveForm.invalid) {
      this.approveForm.markAllAsTouched();
      return;
    }
    const { annualRate, message } = this.approveForm.getRawValue();
    this.decide(
      loan,
      this.api.approve(loan.id, { annualRate: Number(annualRate), message: message.trim() || null }),
      'aprobado',
      () => this.approving.set(null),
    );
  }

  protected openReject(loan: Loan): void {
    this.rejectForm.reset();
    this.rejecting.set(loan);
  }

  protected confirmReject(): void {
    const loan = this.rejecting();
    if (!loan || this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    const comment = this.rejectForm.controls.comment.value.trim() || null;
    this.decide(loan, this.api.reject(loan.id, comment), 'rechazado', () => this.rejecting.set(null));
  }

  private decide(loan: Loan, request: Observable<Loan>, verb: string, onDone?: () => void): void {
    this.busyId.set(loan.id);
    this.notice.set(null);
    this.error.set(null);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (reviewed) => {
        this.busyId.set(null);
        onDone?.();
        this.notice.set(`Préstamo de ${reviewed.applicantEmail ?? loan.applicantEmail} ${verb}.`);
        this.afterChange(reviewed);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        onDone?.();
        if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Conflict) {
          // Another admin decided first (or the applicant cancelled); show the current state.
          this.error.set('Esta solicitud ya fue resuelta por otra persona. La lista se actualizó.');
          this.reloadCount.update((count) => count + 1);
          return;
        }
        this.error.set(problemMessage(error));
      },
    });
  }

  /** In a filtered tab the reviewed loan no longer belongs there, so the page is reloaded; in "Todos" it is updated in place. */
  private afterChange(reviewed: Loan): void {
    if (this.status() === null) {
      this.page.update((page) =>
        page
          ? {
              ...page,
              content: page.content.map((loan) =>
                loan.id === reviewed.id
                  ? { ...reviewed, applicantEmail: loan.applicantEmail, applicantName: loan.applicantName }
                  : loan,
              ),
            }
          : page,
      );
      return;
    }
    const isLastRowOnPage = this.page()?.content.length === 1 && this.pageIndex() > 0;
    if (isLastRowOnPage) {
      this.pageIndex.update((index) => index - 1);
    } else {
      this.reloadCount.update((count) => count + 1);
    }
  }
}
