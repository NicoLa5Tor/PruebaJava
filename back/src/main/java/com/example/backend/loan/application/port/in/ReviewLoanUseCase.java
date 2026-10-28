package com.example.backend.loan.application.port.in;

import com.example.backend.loan.domain.model.Loan;
import java.util.UUID;

/** Admin decisions on pending requests. */
public interface ReviewLoanUseCase {

    Loan approve(UUID loanId, UUID reviewerId);

    Loan reject(UUID loanId, UUID reviewerId, String comment);
}
