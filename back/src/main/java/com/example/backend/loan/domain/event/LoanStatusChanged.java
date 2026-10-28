package com.example.backend.loan.domain.event;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanOffer;
import com.example.backend.loan.domain.model.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published after a review commits, so the applicant's open screen can update without polling. Approvals carry
 * the offer (rate, installment and total) so the screen can show it immediately.
 */
public record LoanStatusChanged(UUID loanId, UUID applicantId, LoanStatus status, String reviewComment,
                                Instant reviewedAt, BigDecimal annualRate, BigDecimal monthlyPayment,
                                BigDecimal totalPayable, BigDecimal totalInterest) {

    public static LoanStatusChanged of(Loan loan) {
        LoanOffer offer = loan.offer();
        return new LoanStatusChanged(loan.id(), loan.applicantId(), loan.status(), loan.reviewComment(),
                loan.reviewedAt(), offer == null ? null : offer.annualRate(),
                offer == null ? null : offer.monthlyPayment(), offer == null ? null : offer.totalPayable(),
                offer == null ? null : offer.totalInterest());
    }
}
