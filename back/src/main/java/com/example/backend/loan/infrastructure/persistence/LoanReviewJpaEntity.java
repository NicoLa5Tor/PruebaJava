package com.example.backend.loan.infrastructure.persistence;

import com.example.backend.loan.domain.model.LoanReview;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** Append-only; {@code createdAt} is the moment the decision was stored. */
@Entity(name = "LoanReview")
@Table(name = "loan_reviews")
class LoanReviewJpaEntity extends BaseJpaEntity {

    @Column(name = "loan_id", nullable = false, updatable = false)
    private UUID loanId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LoanStatus decision;

    @Column(length = 500, updatable = false)
    private String comment;

    static LoanReviewJpaEntity from(LoanReview review) {
        LoanReviewJpaEntity entity = new LoanReviewJpaEntity();
        entity.loanId = review.loanId();
        entity.reviewerId = review.reviewerId();
        entity.decision = review.decision();
        entity.comment = review.comment();
        return entity;
    }

    UUID getLoanId() {
        return loanId;
    }

    LoanStatus getDecision() {
        return decision;
    }
}
