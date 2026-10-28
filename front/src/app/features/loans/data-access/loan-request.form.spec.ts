import { TestBed } from '@angular/core/testing';
import { NonNullableFormBuilder } from '@angular/forms';
import { buildLoanRequestForm } from './loan-request.form';

describe('buildLoanRequestForm', () => {
  const create = () => buildLoanRequestForm(TestBed.inject(NonNullableFormBuilder));

  it('starts invalid until an amount is entered, with a 12 month default term', () => {
    const form = create();

    expect(form.controls.termMonths.value).toBe(12);
    expect(form.controls.amount.hasError('required')).toBe(true);
    expect(form.valid).toBe(false);
  });

  it('accepts amounts and terms within the backend limits', () => {
    const form = create();
    form.setValue({ amount: 1500.55, termMonths: 36 });

    expect(form.valid).toBe(true);
  });

  it.each([
    [99, 'min'],
    [100_000_001, 'max'],
    [1000.123, 'decimals'],
  ])('rejects the amount %s (%s)', (amount, error) => {
    const form = create();
    form.controls.amount.setValue(amount);

    expect(form.controls.amount.hasError(error)).toBe(true);
  });

  it.each([
    [0, 'min'],
    [121, 'max'],
    [12.5, 'pattern'],
  ])('rejects the term %s (%s)', (term, error) => {
    const form = create();
    form.controls.termMonths.setValue(term);

    expect(form.controls.termMonths.hasError(error)).toBe(true);
  });
});
