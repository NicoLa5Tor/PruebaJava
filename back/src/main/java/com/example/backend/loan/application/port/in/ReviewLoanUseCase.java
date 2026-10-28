package com.example.backend.loan.application.port.in;

import com.example.backend.loan.domain.model.Loan;
import java.math.BigDecimal;
import java.util.UUID;

/** Admin decisions on pending requests. Both notify the applicant in the same transaction. */
public interface ReviewLoanUseCase {

    /** @param annualRatePercent effective annual rate (EA) as a percentage; the installment is derived from it */
    Loan approve(UUID loanId, UUID reviewerId, BigDecimal annualRatePercent, String message);

    Loan reject(UUID loanId, UUID reviewerId, String comment);
}
