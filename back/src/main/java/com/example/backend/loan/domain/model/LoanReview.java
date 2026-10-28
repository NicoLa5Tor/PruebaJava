package com.example.backend.loan.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Audit entry recorded together with every approval or rejection. */
public record LoanReview(UUID loanId, UUID reviewerId, LoanStatus decision, String comment, Instant reviewedAt) {

    public static LoanReview of(Loan reviewed) {
        return new LoanReview(reviewed.id(), reviewed.reviewedBy(), reviewed.status(), reviewed.reviewComment(),
                reviewed.reviewedAt());
    }
}
