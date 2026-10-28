package com.example.backend.loan.application.service;

import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;

/** Shared lookups; another applicant's loan is reported as missing so ids cannot be probed. */
final class LoanLookup {

    private LoanLookup() {
    }

    static ResourceNotFoundException notFound(UUID loanId) {
        return new ResourceNotFoundException("Loan with id %s was not found.".formatted(loanId));
    }

    static Loan forUpdate(LoanRepositoryPort loans, UUID loanId) {
        return loans.findByIdForUpdate(loanId).orElseThrow(() -> notFound(loanId));
    }

    static Loan ownedForUpdate(LoanRepositoryPort loans, UUID loanId, UUID applicantId) {
        Loan loan = forUpdate(loans, loanId);
        if (!loan.isOwnedBy(applicantId)) {
            throw notFound(loanId);
        }
        return loan;
    }
}
