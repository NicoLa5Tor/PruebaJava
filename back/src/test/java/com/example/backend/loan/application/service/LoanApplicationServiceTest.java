package com.example.backend.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.shared.domain.exception.BusinessRuleException;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {
    private static final UUID APPLICANT = UUID.randomUUID();
    private static final UUID LOAN_ID = UUID.randomUUID();

    @Mock private LoanRepositoryPort loans;
    @InjectMocks private LoanApplicationService service;

    @Test
    void requestStoresAPendingLoan() {
        when(loans.countByApplicantAndStatus(APPLICANT, LoanStatus.PENDING)).thenReturn(0L);
        when(loans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = service.request(APPLICANT, new BigDecimal("3000"), 24);

        assertThat(loan.status()).isEqualTo(LoanStatus.PENDING);
        assertThat(loan.amount()).isEqualByComparingTo("3000");
        assertThat(loan.termMonths()).isEqualTo(24);
    }

    @Test
    void requestIsRefusedWhenTooManyArePending() {
        when(loans.countByApplicantAndStatus(APPLICANT, LoanStatus.PENDING))
                .thenReturn((long) LoanApplicationService.MAX_PENDING_PER_APPLICANT);

        assertThatThrownBy(() -> service.request(APPLICANT, new BigDecimal("3000"), 24))
                .isInstanceOf(BusinessRuleException.class);
        verify(loans, never()).save(any());
    }

    @Test
    void invalidTermsAreRejectedBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.request(APPLICANT, new BigDecimal("50"), 24))
                .isInstanceOf(BusinessRuleException.class);
        verify(loans, never()).save(any());
    }

    @Test
    void cancelMarksTheOwnLoanAsCancelled() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(pendingOf(APPLICANT)));
        when(loans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.cancel(LOAN_ID, APPLICANT).status()).isEqualTo(LoanStatus.CANCELLED);
    }

    @Test
    void anotherApplicantsLoanLooksMissing() {
        when(loans.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(pendingOf(UUID.randomUUID())));

        assertThatThrownBy(() -> service.cancel(LOAN_ID, APPLICANT)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.modify(LOAN_ID, APPLICANT, new BigDecimal("1000"), 6))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(loans, never()).save(any());
    }

    private static Loan pendingOf(UUID applicant) {
        Instant now = Instant.now();
        return Loan.restore(LOAN_ID, applicant, new BigDecimal("1000"), 12, LoanStatus.PENDING, null, null, null, null, 0L,
                now, now);
    }
}
