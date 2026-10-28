package com.example.backend.loan.application.port.in;

import com.example.backend.loan.domain.model.Loan;
import java.math.BigDecimal;
import java.util.UUID;

/** What an applicant can do with their own requests. */
public interface RequestLoanUseCase {

    Loan request(UUID applicantId, BigDecimal amount, int termMonths);

    Loan modify(UUID loanId, UUID applicantId, BigDecimal amount, int termMonths);

    Loan cancel(UUID loanId, UUID applicantId);
}
