package com.example.backend.loan.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLoanReviewRepository extends JpaRepository<LoanReviewJpaEntity, UUID> {

    List<LoanReviewJpaEntity> findByLoanId(UUID loanId);
}
