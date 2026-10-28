package com.example.backend.loan.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.loan.domain.exception.InvalidLoanStateException;
import com.example.backend.shared.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoanTest {
    private static final UUID APPLICANT = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final BigDecimal RATE = new BigDecimal("24");

    @Nested
    class Request {

        @Test
        void startsPendingWithoutReview() {
            Loan loan = Loan.request(APPLICANT, new BigDecimal("1000"), 12);

            assertThat(loan.status()).isEqualTo(LoanStatus.PENDING);
            assertThat(loan.applicantId()).isEqualTo(APPLICANT);
            assertThat(loan.reviewedBy()).isNull();
            assertThat(loan.reviewedAt()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"100, 1", "100000000, 120", "1500.50, 36"})
        void acceptsValuesWithinLimits(String amount, int term) {
            assertThat(Loan.request(APPLICANT, new BigDecimal(amount), term).amount()).isEqualByComparingTo(amount);
        }

        @ParameterizedTest
        @CsvSource({"99.99, 12", "100000000.01, 12", "1000, 0", "1000, 121", "1000.123, 12"})
        void rejectsValuesOutsideLimits(String amount, int term) {
            assertThatThrownBy(() -> Loan.request(APPLICANT, new BigDecimal(amount), term))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class Review {

        @Test
        void approveRecordsReviewerAndTime() {
            Loan approved = pending().approve(REVIEWER, RATE, null, NOW);

            assertThat(approved.status()).isEqualTo(LoanStatus.APPROVED);
            assertThat(approved.reviewedBy()).isEqualTo(REVIEWER);
            assertThat(approved.reviewedAt()).isEqualTo(NOW);
        }

        @Test
        void rejectKeepsTrimmedComment() {
            Loan rejected = pending().reject(REVIEWER, "  Ingresos insuficientes  ", NOW);

            assertThat(rejected.status()).isEqualTo(LoanStatus.REJECTED);
            assertThat(rejected.reviewComment()).isEqualTo("Ingresos insuficientes");
        }

        @Test
        void transitionsReturnNewInstancesAndLeaveTheOriginalUntouched() {
            Loan original = pending();
            original.approve(REVIEWER, RATE, null, NOW);

            assertThat(original.status()).isEqualTo(LoanStatus.PENDING);
        }

        @Test
        void onlyPendingLoansCanBeReviewed() {
            Loan approved = pending().approve(REVIEWER, RATE, null, NOW);

            assertThatThrownBy(() -> approved.approve(REVIEWER, RATE, null, NOW)).isInstanceOf(InvalidLoanStateException.class);
            assertThatThrownBy(() -> approved.reject(REVIEWER, null, NOW)).isInstanceOf(InvalidLoanStateException.class);
        }

        @Test
        void applicantCannotReviewTheirOwnRequest() {
            assertThatThrownBy(() -> pending().approve(APPLICANT, RATE, null, NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("own loan");
        }

        @Test
        void commentIsLimited() {
            String longComment = "x".repeat(Loan.MAX_COMMENT_LENGTH + 1);

            assertThatThrownBy(() -> pending().reject(REVIEWER, longComment, NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class ApplicantChanges {

        @Test
        void applicantCanModifyAndCancelWhilePending() {
            Loan modified = pending().modify(APPLICANT, new BigDecimal("2500"), 24);
            assertThat(modified.amount()).isEqualByComparingTo("2500");
            assertThat(modified.termMonths()).isEqualTo(24);

            assertThat(modified.cancel(APPLICANT).status()).isEqualTo(LoanStatus.CANCELLED);
        }

        @Test
        void otherUsersCannotChangeTheRequest() {
            UUID stranger = UUID.randomUUID();

            assertThatThrownBy(() -> pending().cancel(stranger)).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> pending().modify(stranger, BigDecimal.TEN, 1)).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void reviewedLoansCannotBeChanged() {
            Loan approved = pending().approve(REVIEWER, RATE, null, NOW);

            assertThatThrownBy(() -> approved.cancel(APPLICANT)).isInstanceOf(InvalidLoanStateException.class);
            assertThatThrownBy(() -> approved.modify(APPLICANT, new BigDecimal("500"), 6))
                    .isInstanceOf(InvalidLoanStateException.class);
        }
    }

    private static Loan pending() {
        return Loan.restore(UUID.randomUUID(), APPLICANT, new BigDecimal("1000"), 12, LoanStatus.PENDING, null, null,
                null, null, 0L, NOW, NOW);
    }
}
