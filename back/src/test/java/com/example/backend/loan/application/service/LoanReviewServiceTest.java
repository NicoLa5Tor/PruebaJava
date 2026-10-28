package com.example.backend.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.backend.loan.application.port.out.LoanEventPublisherPort;
import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.application.port.out.LoanReviewLogPort;
import com.example.backend.loan.domain.event.LoanStatusChanged;
import com.example.backend.loan.domain.exception.InvalidLoanStateException;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanReview;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanReviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final UUID LOAN_ID = UUID.randomUUID();
    private static final UUID APPLICANT = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();

    @Mock private LoanRepositoryPort loans;
    @Mock private LoanReviewLogPort reviewLog;
    @Mock private LoanEventPublisherPort events;
    private LoanReviewService service;

    @BeforeEach
    void setUp() {
        service = new LoanReviewService(loans, reviewLog, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approveLocksSavesAuditsAndPublishesInThatOrder() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan(LoanStatus.PENDING)));
        when(loans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Loan approved = service.approve(LOAN_ID, REVIEWER);

        assertThat(approved.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo(REVIEWER);
        assertThat(approved.reviewedAt()).isEqualTo(NOW);
        InOrder order = inOrder(loans, reviewLog, events);
        order.verify(loans).findByIdForUpdate(LOAN_ID);
        order.verify(loans).save(approved);
        order.verify(reviewLog).record(new LoanReview(LOAN_ID, REVIEWER, LoanStatus.APPROVED, null, NOW));
        order.verify(events).publish(LoanStatusChanged.of(approved));
    }

    @Test
    void rejectStoresTheComment() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan(LoanStatus.PENDING)));
        when(loans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Loan rejected = service.reject(LOAN_ID, REVIEWER, "Historial crediticio insuficiente");

        assertThat(rejected.status()).isEqualTo(LoanStatus.REJECTED);
        ArgumentCaptor<LoanReview> review = ArgumentCaptor.forClass(LoanReview.class);
        verify(reviewLog).record(review.capture());
        assertThat(review.getValue().decision()).isEqualTo(LoanStatus.REJECTED);
        assertThat(review.getValue().comment()).isEqualTo("Historial crediticio insuficiente");
    }

    @Test
    void unknownLoanIsNotFound() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(LOAN_ID, REVIEWER)).isInstanceOf(ResourceNotFoundException.class);
        verify(loans, never()).save(any());
        verifyNoInteractions(reviewLog, events);
    }

    @Test
    void alreadyReviewedLoanIsAConflictAndNothingIsWritten() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan(LoanStatus.APPROVED)));

        assertThatThrownBy(() -> service.reject(LOAN_ID, REVIEWER, null)).isInstanceOf(InvalidLoanStateException.class);
        verify(loans, never()).save(any());
        verifyNoInteractions(reviewLog, events);
    }

    @Test
    void auditFailurePropagatesSoTheTransactionRollsBack() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan(LoanStatus.PENDING)));
        when(loans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RuntimeException failure = new IllegalStateException("audit table unavailable");
        org.mockito.Mockito.doThrow(failure).when(reviewLog).record(any());

        assertThatThrownBy(() -> service.approve(LOAN_ID, REVIEWER)).isSameAs(failure);
        verifyNoInteractions(events);
    }

    private static Loan loan(LoanStatus status) {
        return Loan.restore(LOAN_ID, APPLICANT, new BigDecimal("5000"), 12, status, null, null, null, 0L, NOW, NOW);
    }
}
