package com.example.backend.loan.infrastructure.persistence;

import com.example.backend.loan.application.port.out.LoanReviewLogPort;
import com.example.backend.loan.domain.model.LoanReview;
import org.springframework.stereotype.Component;

@Component
class LoanReviewLogAdapter implements LoanReviewLogPort {

    private final SpringDataLoanReviewRepository repository;

    LoanReviewLogAdapter(SpringDataLoanReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(LoanReview review) {
        repository.save(LoanReviewJpaEntity.from(review));
    }
}
