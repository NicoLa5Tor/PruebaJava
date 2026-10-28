package com.example.backend.loan.application.service;

import com.example.backend.loan.application.port.in.ReviewLoanUseCase;
import com.example.backend.loan.application.port.out.ApplicantNotifierPort;
import com.example.backend.loan.application.port.out.LoanEventPublisherPort;
import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.application.port.out.LoanReviewLogPort;
import com.example.backend.loan.domain.event.LoanStatusChanged;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanReview;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each decision is one transaction: lock the loan row, apply the transition (an approval computes the offer), store
 * the loan, its audit entry and the applicant's notification. If any step fails nothing is written, and the live
 * status event is only delivered once the transaction commits.
 */
@Service
public class LoanReviewService implements ReviewLoanUseCase {

    private final LoanRepositoryPort loans;
    private final LoanReviewLogPort reviewLog;
    private final LoanEventPublisherPort events;
    private final ApplicantNotifierPort notifier;
    private final Clock clock;

    public LoanReviewService(LoanRepositoryPort loans, LoanReviewLogPort reviewLog, LoanEventPublisherPort events,
                             ApplicantNotifierPort notifier, Clock clock) {
        this.loans = loans;
        this.reviewLog = reviewLog;
        this.events = events;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Loan approve(UUID loanId, UUID reviewerId, BigDecimal annualRatePercent, String message) {
        return decide(loanId, loan -> loan.approve(reviewerId, annualRatePercent, message, clock.instant()));
    }

    @Override
    @Transactional
    public Loan reject(UUID loanId, UUID reviewerId, String comment) {
        return decide(loanId, loan -> loan.reject(reviewerId, comment, clock.instant()));
    }

    private Loan decide(UUID loanId, UnaryOperator<Loan> transition) {
        Loan pending = LoanLookup.forUpdate(loans, loanId);
        Loan reviewed = loans.save(transition.apply(pending));
        reviewLog.record(LoanReview.of(reviewed));
        notifier.notifyDecision(reviewed);
        events.publish(LoanStatusChanged.of(reviewed));
        return reviewed;
    }
}
