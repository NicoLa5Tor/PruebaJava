package com.example.backend.loan.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.backend.shared.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoanOfferTest {

    @Test
    void zeroRateSplitsThePrincipalEvenly() {
        LoanOffer offer = LoanOffer.calculate(new BigDecimal("1200000"), 12, BigDecimal.ZERO);

        assertThat(offer.monthlyPayment()).isEqualByComparingTo("100000.00");
        assertThat(offer.totalPayable()).isEqualByComparingTo("1200000.00");
        assertThat(offer.totalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void effectiveAnnualRateIsConvertedToItsMonthlyEquivalent() {
        // 26.82 % EA is ~2 % per month: (1.02)^12 - 1 = 0.268242
        BigDecimal monthly = LoanOffer.monthlyRateFrom(new BigDecimal("26.8242"));

        assertThat(monthly.doubleValue()).isCloseTo(0.02, within(1e-6));
    }

    @Test
    void frenchAmortizationMatchesTheTextbookInstallment() {
        // 1,000,000 over 12 months at 2 % monthly -> 94,559.60 per month (standard annuity formula).
        LoanOffer offer = LoanOffer.calculate(new BigDecimal("1000000"), 12, new BigDecimal("26.82"));

        assertThat(offer.monthlyPayment().doubleValue()).isCloseTo(94_559.60, within(5.0));
        assertThat(offer.totalPayable()).isEqualByComparingTo(offer.monthlyPayment().multiply(BigDecimal.valueOf(12)));
        assertThat(offer.totalInterest())
                .isEqualByComparingTo(offer.totalPayable().subtract(new BigDecimal("1000000")));
    }

    @Test
    void higherRatesAndLongerTermsCostMore() {
        BigDecimal principal = new BigDecimal("5000000");
        LoanOffer cheap = LoanOffer.calculate(principal, 24, new BigDecimal("12"));
        LoanOffer expensive = LoanOffer.calculate(principal, 24, new BigDecimal("30"));
        LoanOffer longer = LoanOffer.calculate(principal, 60, new BigDecimal("12"));

        assertThat(expensive.monthlyPayment()).isGreaterThan(cheap.monthlyPayment());
        assertThat(longer.monthlyPayment()).isLessThan(cheap.monthlyPayment());
        assertThat(longer.totalInterest()).isGreaterThan(cheap.totalInterest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.01", "12.345"})
    void rejectsRatesOutsideTheAllowedRange(String rate) {
        assertThatThrownBy(() -> LoanOffer.calculate(new BigDecimal("1000"), 12, new BigDecimal(rate)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void approvalStoresTheOfferAndTheMessage() {
        Loan pending = Loan.restore(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1500000"), 24,
                LoanStatus.PENDING, null, null, null, null, 0L, Instant.now(), Instant.now());

        Loan approved = pending.approve(UUID.randomUUID(), new BigDecimal("24.5"), "  Bienvenido  ", Instant.now());

        assertThat(approved.offer().annualRate()).isEqualByComparingTo("24.50");
        assertThat(approved.offer().monthlyPayment()).isPositive();
        assertThat(approved.reviewComment()).isEqualTo("Bienvenido");
        assertThat(pending.reject(UUID.randomUUID(), "No", Instant.now()).offer()).isNull();
    }
}
