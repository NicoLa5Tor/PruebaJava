package com.example.backend.loan.application.port.out;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.domain.model.LoanView;
import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepositoryPort {

    Optional<Loan> findById(UUID id);

    /** Locks the row until the surrounding transaction ends, so two reviewers cannot decide the same loan. */
    Optional<Loan> findByIdForUpdate(UUID id);

    /** Newest first. */
    List<Loan> findByApplicant(UUID applicantId);

    long countByApplicantAndStatus(UUID applicantId, LoanStatus status);

    /** Newest first; a null status lists every loan. */
    PageResult<LoanView> search(LoanStatus status, PageQuery page);

    Loan save(Loan loan);
}
