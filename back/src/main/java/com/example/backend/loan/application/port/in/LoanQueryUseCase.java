package com.example.backend.loan.application.port.in;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.domain.model.LoanView;
import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import java.util.List;
import java.util.UUID;

public interface LoanQueryUseCase {

    List<Loan> findMine(UUID applicantId);

    /** Applicants only see their own loans; for anyone else the loan does not exist. */
    Loan findById(UUID loanId, UUID requesterId, boolean requesterIsReviewer);

    PageResult<LoanView> search(LoanStatus status, PageQuery page);
}
