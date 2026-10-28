import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/api/api-client.service';
import { PageResponse } from '../../../core/api/page-response';
import { Loan, LoanRequestPayload, LoanStatus } from './loan.models';

export interface LoanQuery {
  status: LoanStatus | null;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class LoansApiService {
  private readonly api = inject(ApiClientService);

  mine(): Observable<Loan[]> {
    return this.api.get<Loan[]>('loans/me');
  }

  request(payload: LoanRequestPayload): Observable<Loan> {
    return this.api.post<Loan>('loans', payload);
  }

  modify(id: string, payload: LoanRequestPayload): Observable<Loan> {
    return this.api.put<Loan>(`loans/${id}`, payload);
  }

  cancel(id: string): Observable<Loan> {
    return this.api.patch<Loan>(`loans/${id}/cancel`, null);
  }

  search({ status, page, size }: LoanQuery): Observable<PageResponse<Loan>> {
    return this.api.get<PageResponse<Loan>>('loans', { params: { page, size, ...(status ? { status } : {}) } });
  }

  approve(id: string): Observable<Loan> {
    return this.api.patch<Loan>(`loans/${id}/approve`, null);
  }

  reject(id: string, comment: string | null): Observable<Loan> {
    return this.api.patch<Loan>(`loans/${id}/reject`, { comment });
  }
}
