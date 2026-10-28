import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Loan } from './loan.models';
import { LoansApiService } from './loans-api.service';
import { MyLoansStore } from './my-loans.store';

function loan(overrides: Partial<Loan> = {}): Loan {
  return {
    id: 'loan-1',
    applicantId: 'user-1',
    amount: 1000,
    termMonths: 12,
    status: 'PENDING',
    createdAt: '2026-10-28T10:00:00Z',
    updatedAt: '2026-10-28T10:00:00Z',
    ...overrides,
  };
}

describe('MyLoansStore', () => {
  let api: { [K in keyof LoansApiService]?: ReturnType<typeof vi.fn> };
  let store: MyLoansStore;

  beforeEach(() => {
    api = { mine: vi.fn(), request: vi.fn(), modify: vi.fn(), cancel: vi.fn() };
    TestBed.configureTestingModule({ providers: [MyLoansStore, { provide: LoansApiService, useValue: api }] });
    store = TestBed.inject(MyLoansStore);
  });

  it('loads the loans and derives the counters', async () => {
    api.mine!.mockReturnValue(
      of([loan(), loan({ id: 'loan-2', status: 'APPROVED', amount: 2000 }), loan({ id: 'loan-3', status: 'APPROVED', amount: 500 })]),
    );

    await store.load();

    expect(store.loans()).toHaveLength(3);
    expect(store.pendingCount()).toBe(1);
    expect(store.approvedTotal()).toBe(2500);
    expect(store.canRequestMore()).toBe(true);
  });

  it('blocks new requests once three are pending', async () => {
    api.mine!.mockReturnValue(of([loan({ id: 'a' }), loan({ id: 'b' }), loan({ id: 'c' })]));

    await store.load();

    expect(store.canRequestMore()).toBe(false);
  });

  it('puts a new request at the top of the list', async () => {
    api.mine!.mockReturnValue(of([loan()]));
    api.request!.mockReturnValue(of(loan({ id: 'new', amount: 5000 })));
    await store.load();

    await store.request({ amount: 5000, termMonths: 24 });

    expect(store.loans().map((item) => item.id)).toEqual(['new', 'loan-1']);
  });

  it('cancels optimistically and rolls back when the server refuses', async () => {
    const response = new Subject<Loan>();
    api.mine!.mockReturnValue(of([loan()]));
    api.cancel!.mockReturnValue(response);
    await store.load();

    const cancelling = store.cancel('loan-1');
    expect(store.loans()[0].status).toBe('CANCELLED');

    response.error(new HttpErrorResponse({ status: 409, error: { detail: 'Loan was already APPROVED.' } }));
    await cancelling;

    expect(store.loans()[0].status).toBe('PENDING');
    expect(store.error()).toBe('Loan was already APPROVED.');
  });

  it('applies status changes pushed by the server', async () => {
    api.mine!.mockReturnValue(of([loan()]));
    await store.load();

    const updated = store.applyStatusChange({
      loanId: 'loan-1',
      applicantId: 'user-1',
      status: 'REJECTED',
      reviewComment: 'Ingresos insuficientes',
      reviewedAt: '2026-10-28T11:00:00Z',
    });

    expect(updated?.status).toBe('REJECTED');
    expect(store.loans()[0].reviewComment).toBe('Ingresos insuficientes');
    expect(store.applyStatusChange({ ...updated!, loanId: 'unknown' } as never)).toBeNull();
  });

  it('reports load errors without throwing', async () => {
    api.mine!.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    await store.load();

    expect(store.error()).toBe('No fue posible conectar con el servidor.');
    expect(store.loading()).toBe(false);
  });
});
