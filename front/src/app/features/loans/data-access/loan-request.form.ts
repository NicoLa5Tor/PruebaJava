import { AbstractControl, NonNullableFormBuilder, ValidationErrors, Validators } from '@angular/forms';
import { LOAN_LIMITS } from './loan.models';

/** At most two decimals, like the backend's @Digits(fraction = 2). */
export function maxTwoDecimals(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || Number.isNaN(value)) {
    return null;
  }
  // Checked on the text form: multiplying by 100 is not exact in floating point (1500.55 * 100 !== 150055).
  return /^-?\d+(\.\d{1,2})?$/.test(String(value)) ? null : { decimals: true };
}

/** Reactive form for requesting or modifying a loan; the limits mirror the backend's validation. */
export function buildLoanRequestForm(fb: NonNullableFormBuilder) {
  return fb.group({
    amount: fb.control<number | null>(null, [
      Validators.required,
      Validators.min(LOAN_LIMITS.minAmount),
      Validators.max(LOAN_LIMITS.maxAmount),
      maxTwoDecimals,
    ]),
    termMonths: fb.control<number | null>(12, [
      Validators.required,
      Validators.min(LOAN_LIMITS.minTermMonths),
      Validators.max(LOAN_LIMITS.maxTermMonths),
      Validators.pattern(/^\d+$/),
    ]),
  });
}

export type LoanRequestForm = ReturnType<typeof buildLoanRequestForm>;
