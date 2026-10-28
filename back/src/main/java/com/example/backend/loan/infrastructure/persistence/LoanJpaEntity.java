package com.example.backend.loan.infrastructure.persistence;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanOffer;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public only so the JDK proxy behind {@code SpringDataLoanRepository.LoanRow} can return it. */
@Entity(name = "Loan")
@Table(name = "loans")
public class LoanJpaEntity extends BaseJpaEntity {

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private UUID applicantId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    @Column(length = 500)
    private String reviewComment;

    private UUID reviewedBy;

    private Instant reviewedAt;

    @Column(precision = 5, scale = 2)
    private BigDecimal annualRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalPayable;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalInterest;

    /** Optimistic lock: a stale write (e.g. a lost race outside the row lock) fails with 409 instead of overwriting. */
    @Version
    @Column(nullable = false)
    private Long version;

    void apply(Loan loan) {
        applicantId = loan.applicantId();
        amount = loan.amount();
        termMonths = loan.termMonths();
        status = loan.status();
        reviewComment = loan.reviewComment();
        reviewedBy = loan.reviewedBy();
        reviewedAt = loan.reviewedAt();
        LoanOffer offer = loan.offer();
        annualRate = offer == null ? null : offer.annualRate();
        monthlyPayment = offer == null ? null : offer.monthlyPayment();
        totalPayable = offer == null ? null : offer.totalPayable();
        totalInterest = offer == null ? null : offer.totalInterest();
    }

    Loan toDomain() {
        LoanOffer offer = annualRate == null ? null
                : new LoanOffer(annualRate, monthlyPayment, totalPayable, totalInterest);
        return Loan.restore(getId(), applicantId, amount, termMonths, status, reviewComment, reviewedBy, reviewedAt,
                offer, version, getCreatedAt(), getUpdatedAt());
    }
}
