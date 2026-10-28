package com.example.backend.loan.application.service;

import com.example.backend.loan.application.port.in.LoanQueryUseCase;
import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.domain.model.LoanView;
import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoanQueryService implements LoanQueryUseCase {

    private final LoanRepositoryPort loans;

    public LoanQueryService(LoanRepositoryPort loans) {
        this.loans = loans;
    }

    @Override
    public List<Loan> findMine(UUID applicantId) {
        return loans.findByApplicant(applicantId);
    }

    @Override
    public Loan findById(UUID loanId, UUID requesterId, boolean requesterIsReviewer) {
        return loans.findById(loanId)
                .filter(loan -> requesterIsReviewer || loan.isOwnedBy(requesterId))
                .orElseThrow(() -> LoanLookup.notFound(loanId));
    }

    @Override
    public PageResult<LoanView> search(LoanStatus status, PageQuery page) {
        return loans.search(status, page);
    }
}
