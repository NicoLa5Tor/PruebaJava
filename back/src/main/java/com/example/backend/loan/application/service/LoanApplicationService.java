package com.example.backend.loan.application.service;

import com.example.backend.loan.application.port.in.RequestLoanUseCase;
import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoanApplicationService implements RequestLoanUseCase {
    static final int MAX_PENDING_PER_APPLICANT = 3;

    private final LoanRepositoryPort loans;

    public LoanApplicationService(LoanRepositoryPort loans) {
        this.loans = loans;
    }

    @Override
    public Loan request(UUID applicantId, BigDecimal amount, int termMonths) {
        Loan loan = Loan.request(applicantId, amount, termMonths);
        if (loans.countByApplicantAndStatus(applicantId, LoanStatus.PENDING) >= MAX_PENDING_PER_APPLICANT) {
            throw new BusinessRuleException("You already have %d pending loan requests. Wait for a decision or cancel one."
                    .formatted(MAX_PENDING_PER_APPLICANT));
        }
        return loans.save(loan);
    }

    @Override
    public Loan modify(UUID loanId, UUID applicantId, BigDecimal amount, int termMonths) {
        Loan loan = LoanLookup.ownedForUpdate(loans, loanId, applicantId);
        return loans.save(loan.modify(applicantId, amount, termMonths));
    }

    @Override
    public Loan cancel(UUID loanId, UUID applicantId) {
        Loan loan = LoanLookup.ownedForUpdate(loans, loanId, applicantId);
        return loans.save(loan.cancel(applicantId));
    }
}
