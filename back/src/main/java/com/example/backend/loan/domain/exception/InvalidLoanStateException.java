package com.example.backend.loan.domain.exception;

import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.domain.exception.ConflictException;
import java.util.UUID;

/** The loan already left PENDING (for example, another admin reviewed it first). */
public class InvalidLoanStateException extends ConflictException {

    public InvalidLoanStateException(UUID loanId, LoanStatus current, String action) {
        super("Loan %s cannot be %s because it is %s.".formatted(loanId, action, current));
    }
}
