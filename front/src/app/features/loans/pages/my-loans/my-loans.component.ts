import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { map, startWith } from 'rxjs';
import { problemMessage } from '../../../../core/api/problem-message';
import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { displayNameOf } from '../../../../core/auth/auth.models';
import { LoanEventsService } from '../../data-access/loan-events.service';
import { buildLoanRequestForm } from '../../data-access/loan-request.form';
import { LOAN_LIMITS, LOAN_STATUS_BADGES, LOAN_STATUS_LABELS, Loan } from '../../data-access/loan.models';
import { MyLoansStore } from '../../data-access/my-loans.store';

const HIGHLIGHT_MS = 2_500;

@Component({
  selector: 'app-my-loans',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './my-loans.component.html',
  providers: [MyLoansStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyLoansComponent {
  protected readonly store = inject(MyLoansStore);
  private readonly session = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly limits = LOAN_LIMITS;
  protected readonly statusLabels = LOAN_STATUS_LABELS;
  protected readonly statusBadges = LOAN_STATUS_BADGES;
  protected readonly quickTerms = [6, 12, 24, 36, 60];
  protected readonly placeholderRows = [0, 1, 2];

  protected readonly firstName = computed(() => {
    const user = this.session.currentUser();
    return user ? (user.firstName ?? displayNameOf(user)) : '';
  });
  protected readonly form = buildLoanRequestForm(inject(NonNullableFormBuilder));
  protected readonly editing = signal<Loan | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly toast = signal<string | null>(null);
  protected readonly highlighted = signal<string | null>(null);
  private readonly events = inject(LoanEventsService);
  protected readonly live = this.events.connected;

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(this.form.value), map(() => this.form.value)), {
    requireSync: true,
  });
  /** Informative only: the bank's real installment depends on the interest rate. */
  protected readonly estimatedInstallment = computed(() => {
    const { amount, termMonths } = this.formValue();
    return amount && termMonths && this.form.valid ? amount / termMonths : null;
  });

  constructor() {
    void this.store.load();
    this.events
      .statusChanges()
      .pipe(takeUntilDestroyed())
      .subscribe((change) => {
        const updated = this.store.applyStatusChange(change);
        if (updated) {
          this.flash(updated.id);
          const installment = updated.monthlyPayment ? ` Cuota: ${formatAmount(updated.monthlyPayment)} al mes.` : '';
          this.showToast(
            `Tu préstamo por ${formatAmount(updated.amount)} fue ${LOAN_STATUS_LABELS[updated.status].toLowerCase()}.${installment}`,
          );
        }
      });
  }

  protected setTerm(months: number): void {
    this.form.controls.termMonths.setValue(months);
    this.form.controls.termMonths.markAsTouched();
  }

  protected startEditing(loan: Loan): void {
    this.editing.set(loan);
    this.formError.set(null);
    this.form.setValue({ amount: loan.amount, termMonths: loan.termMonths });
  }

  protected stopEditing(): void {
    this.editing.set(null);
    this.formError.set(null);
    this.form.reset();
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const payload = { amount: this.form.controls.amount.value!, termMonths: Number(this.form.controls.termMonths.value) };
    const editing = this.editing();
    this.submitting.set(true);
    this.formError.set(null);
    try {
      const saved = editing ? await this.store.modify(editing.id, payload) : await this.store.request(payload);
      this.flash(saved.id);
      this.showToast(editing ? 'Solicitud actualizada.' : 'Solicitud enviada. Te avisaremos cuando sea revisada.');
      this.editing.set(null);
      this.form.reset();
    } catch (error: unknown) {
      this.formError.set(problemMessage(error, 'No fue posible enviar la solicitud.'));
    } finally {
      this.submitting.set(false);
    }
  }

  protected cancel(loan: Loan): void {
    if (this.editing()?.id === loan.id) {
      this.stopEditing();
    }
    void this.store.cancel(loan.id);
  }

  private flash(id: string): void {
    this.highlighted.set(id);
    const timer = setTimeout(() => this.highlighted.set(null), HIGHLIGHT_MS);
    this.destroyRef.onDestroy(() => clearTimeout(timer));
  }

  private showToast(message: string): void {
    this.toast.set(message);
    const timer = setTimeout(() => this.toast.set(null), 5_000);
    this.destroyRef.onDestroy(() => clearTimeout(timer));
  }
}

function formatAmount(amount: number): string {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(amount);
}
