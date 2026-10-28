package com.example.backend.loan.domain.event;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import java.time.Instant;
import java.util.UUID;

/** Published after a review commits, so the applicant's open screen can update without polling. */
public record LoanStatusChanged(UUID loanId, UUID applicantId, LoanStatus status, String reviewComment,
                                Instant reviewedAt) {

    public static LoanStatusChanged of(Loan loan) {
        return new LoanStatusChanged(loan.id(), loan.applicantId(), loan.status(), loan.reviewComment(), loan.reviewedAt());
    }
}
