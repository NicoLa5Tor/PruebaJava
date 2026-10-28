package com.example.backend.loan.domain.model;

import com.example.backend.loan.domain.exception.InvalidLoanStateException;
import com.example.backend.shared.domain.exception.BusinessRuleException;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A loan request and its review. Immutable: every transition returns a new instance, which makes it safe to keep
 * in the cache and keeps the business rules (limits, who may act, which state changes are allowed) in one place.
 */
public final class Loan implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("100");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    public static final int MIN_TERM_MONTHS = 1;
    public static final int MAX_TERM_MONTHS = 120;
    public static final int MAX_COMMENT_LENGTH = 500;

    private final UUID id;
    private final UUID applicantId;
    private final BigDecimal amount;
    private final int termMonths;
    private final LoanStatus status;
    private final String reviewComment;
    private final UUID reviewedBy;
    private final Instant reviewedAt;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Loan(UUID id, UUID applicantId, BigDecimal amount, int termMonths, LoanStatus status, String reviewComment,
                 UUID reviewedBy, Instant reviewedAt, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.termMonths = termMonths;
        this.status = Objects.requireNonNull(status, "status");
        this.reviewComment = reviewComment;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new PENDING request. */
    public static Loan request(UUID applicantId, BigDecimal amount, int termMonths) {
        validateTerms(amount, termMonths);
        return new Loan(null, applicantId, amount, termMonths, LoanStatus.PENDING, null, null, null, null, null, null);
    }

    /** Rebuilds a stored loan without re-running the creation rules. */
    public static Loan restore(UUID id, UUID applicantId, BigDecimal amount, int termMonths, LoanStatus status,
                               String reviewComment, UUID reviewedBy, Instant reviewedAt, Long version,
                               Instant createdAt, Instant updatedAt) {
        return new Loan(id, applicantId, amount, termMonths, status, reviewComment, reviewedBy, reviewedAt, version,
                createdAt, updatedAt);
    }

    /** The applicant changes amount or term while nobody has reviewed the request yet. */
    public Loan modify(UUID requesterId, BigDecimal newAmount, int newTermMonths) {
        ensureOwnedBy(requesterId);
        ensurePending("modified");
        validateTerms(newAmount, newTermMonths);
        return new Loan(id, applicantId, newAmount, newTermMonths, status, null, null, null, version, createdAt, updatedAt);
    }

    public Loan cancel(UUID requesterId) {
        ensureOwnedBy(requesterId);
        ensurePending("cancelled");
        return withStatus(LoanStatus.CANCELLED, null, null, null);
    }

    public Loan approve(UUID reviewerId, Instant now) {
        return review(LoanStatus.APPROVED, reviewerId, null, now, "approved");
    }

    public Loan reject(UUID reviewerId, String comment, Instant now) {
        return review(LoanStatus.REJECTED, reviewerId, comment, now, "rejected");
    }

    public boolean isOwnedBy(UUID userId) {
        return applicantId.equals(userId);
    }

    private Loan review(LoanStatus decision, UUID reviewerId, String comment, Instant now, String action) {
        Objects.requireNonNull(reviewerId, "reviewerId");
        ensurePending(action);
        if (isOwnedBy(reviewerId)) {
            throw new BusinessRuleException("You cannot review your own loan request.");
        }
        String normalized = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalized != null && normalized.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessRuleException("The review comment cannot exceed %d characters.".formatted(MAX_COMMENT_LENGTH));
        }
        return withStatus(decision, normalized, reviewerId, now);
    }

    private Loan withStatus(LoanStatus newStatus, String comment, UUID reviewer, Instant reviewTime) {
        return new Loan(id, applicantId, amount, termMonths, newStatus, comment, reviewer, reviewTime, version,
                createdAt, updatedAt);
    }

    private void ensurePending(String action) {
        if (status != LoanStatus.PENDING) {
            throw new InvalidLoanStateException(id, status, action);
        }
    }

    private void ensureOwnedBy(UUID requesterId) {
        if (!isOwnedBy(requesterId)) {
            throw new BusinessRuleException("Only the applicant can change this loan request.");
        }
    }

    private static void validateTerms(BigDecimal amount, int termMonths) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("The amount must be between %s and %s."
                    .formatted(MIN_AMOUNT.toPlainString(), MAX_AMOUNT.toPlainString()));
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("The amount cannot have more than two decimals.");
        }
        if (termMonths < MIN_TERM_MONTHS || termMonths > MAX_TERM_MONTHS) {
            throw new BusinessRuleException("The term must be between %d and %d months."
                    .formatted(MIN_TERM_MONTHS, MAX_TERM_MONTHS));
        }
    }

    public UUID id() {
        return id;
    }

    public UUID applicantId() {
        return applicantId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public int termMonths() {
        return termMonths;
    }

    public LoanStatus status() {
        return status;
    }

    public String reviewComment() {
        return reviewComment;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public Long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
