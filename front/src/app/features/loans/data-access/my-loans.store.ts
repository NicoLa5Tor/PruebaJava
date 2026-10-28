import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/api/problem-message';
import { LOAN_LIMITS, Loan, LoanRequestPayload, LoanStatusChanged } from './loan.models';
import { LoansApiService } from './loans-api.service';

/**
 * State of the applicant's "Mis préstamos" page. Signals keep it synchronous and readable from templates;
 * every change goes through these methods so the list, the counters and the server stay consistent.
 * Provided by the page component, so it lives and dies with it and never leaks between sessions.
 */
@Injectable()
export class MyLoansStore {
  private readonly api = inject(LoansApiService);

  private readonly loansState = signal<Loan[]>([]);
  private readonly loadingState = signal(false);
  private readonly errorState = signal<string | null>(null);

  readonly loans = this.loansState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  readonly error = this.errorState.asReadonly();
  readonly pendingCount = computed(() => this.loansState().filter((loan) => loan.status === 'PENDING').length);
  readonly canRequestMore = computed(() => this.pendingCount() < LOAN_LIMITS.maxPendingRequests);
  readonly approvedTotal = computed(() =>
    this.loansState()
      .filter((loan) => loan.status === 'APPROVED')
      .reduce((total, loan) => total + loan.amount, 0),
  );

  async load(): Promise<void> {
    this.loadingState.set(true);
    this.errorState.set(null);
    try {
      this.loansState.set(await firstValueFrom(this.api.mine()));
    } catch (error: unknown) {
      this.errorState.set(problemMessage(error, 'No fue posible cargar tus préstamos.'));
    } finally {
      this.loadingState.set(false);
    }
  }

  /** Throws the API error so the form can show it next to the fields. */
  async request(payload: LoanRequestPayload): Promise<Loan> {
    const created = await firstValueFrom(this.api.request(payload));
    this.loansState.update((loans) => [created, ...loans]);
    return created;
  }

  async modify(id: string, payload: LoanRequestPayload): Promise<Loan> {
    const updated = await firstValueFrom(this.api.modify(id, payload));
    this.replace(updated);
    return updated;
  }

  /** Optimistic: the row shows as cancelled at once and is restored if the server refuses. */
  async cancel(id: string): Promise<void> {
    const previous = this.loansState().find((loan) => loan.id === id);
    if (!previous) {
      return;
    }
    this.replace({ ...previous, status: 'CANCELLED' });
    this.errorState.set(null);
    try {
      this.replace(await firstValueFrom(this.api.cancel(id)));
    } catch (error: unknown) {
      this.replace(previous);
      this.errorState.set(problemMessage(error, 'No fue posible cancelar la solicitud.'));
    }
  }

  /** Applies a review pushed by the server; returns the updated loan, or null if it is not in the list. */
  applyStatusChange(change: LoanStatusChanged): Loan | null {
    const current = this.loansState().find((loan) => loan.id === change.loanId);
    if (!current) {
      return null;
    }
    const updated: Loan = {
      ...current,
      status: change.status,
      reviewComment: change.reviewComment ?? undefined,
      reviewedAt: change.reviewedAt ?? undefined,
      annualRate: change.annualRate ?? undefined,
      monthlyPayment: change.monthlyPayment ?? undefined,
      totalPayable: change.totalPayable ?? undefined,
      totalInterest: change.totalInterest ?? undefined,
    };
    this.replace(updated);
    return updated;
  }

  clearError(): void {
    this.errorState.set(null);
  }

  private replace(updated: Loan): void {
    this.loansState.update((loans) => loans.map((loan) => (loan.id === updated.id ? updated : loan)));
  }
}
