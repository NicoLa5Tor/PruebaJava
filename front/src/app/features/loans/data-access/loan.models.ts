export type LoanStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** Mirrors the backend LoanResponse. Applicant fields only come in the admin review queue. */
export interface Loan {
  id: string;
  applicantId: string;
  applicantEmail?: string;
  applicantName?: string;
  amount: number;
  termMonths: number;
  status: LoanStatus;
  reviewComment?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LoanRequestPayload {
  amount: number;
  termMonths: number;
}

/** Pushed by the server-sent events stream when an admin reviews one of the user's loans. */
export interface LoanStatusChanged {
  loanId: string;
  applicantId: string;
  status: LoanStatus;
  reviewComment: string | null;
  reviewedAt: string | null;
}

/** Same limits the backend enforces in the Loan domain model. */
export const LOAN_LIMITS = {
  minAmount: 100,
  maxAmount: 100_000_000,
  minTermMonths: 1,
  maxTermMonths: 120,
  maxCommentLength: 500,
  maxPendingRequests: 3,
} as const;

export const LOAN_STATUS_LABELS: Record<LoanStatus, string> = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
  CANCELLED: 'Cancelado',
};

export const LOAN_STATUS_BADGES: Record<LoanStatus, string> = {
  PENDING: 'status-pending',
  APPROVED: 'status-approved',
  REJECTED: 'status-rejected',
  CANCELLED: 'status-cancelled',
};
