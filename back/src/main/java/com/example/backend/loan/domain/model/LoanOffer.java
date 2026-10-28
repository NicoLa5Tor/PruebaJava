package com.example.backend.loan.domain.model;

import com.example.backend.shared.domain.exception.BusinessRuleException;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Conditions fixed when a loan is approved: the effective annual rate (EA) chosen by the admin and the resulting
 * fixed monthly installment (French amortization), total to repay and total interest. Amounts are rounded to
 * cents; the total is installment × months so the figures the client sees always add up.
 */
public record LoanOffer(BigDecimal annualRate, BigDecimal monthlyPayment, BigDecimal totalPayable,
                        BigDecimal totalInterest) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final BigDecimal MAX_ANNUAL_RATE = new BigDecimal("100");
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param annualRatePercent effective annual rate as a percentage, e.g. 24.5 for 24,5 % EA
     */
    public static LoanOffer calculate(BigDecimal principal, int months, BigDecimal annualRatePercent) {
        if (annualRatePercent == null || annualRatePercent.signum() < 0 || annualRatePercent.compareTo(MAX_ANNUAL_RATE) > 0) {
            throw new BusinessRuleException("The annual rate must be between 0 and %s %%.".formatted(MAX_ANNUAL_RATE));
        }
        if (annualRatePercent.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("The annual rate cannot have more than two decimals.");
        }
        BigDecimal rate = annualRatePercent.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal monthlyRate = monthlyRateFrom(rate);
        BigDecimal payment = monthlyRate.signum() == 0
                ? principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP)
                : installment(principal, months, monthlyRate);
        BigDecimal total = payment.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        return new LoanOffer(rate, payment, total, total.subtract(principal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    /** EA to effective monthly: (1 + EA)^(1/12) − 1. The 12th root has no exact BigDecimal form, so it uses double. */
    static BigDecimal monthlyRateFrom(BigDecimal annualRatePercent) {
        double annual = annualRatePercent.divide(HUNDRED, PRECISION).doubleValue();
        return new BigDecimal(Math.pow(1 + annual, 1.0 / 12) - 1, PRECISION);
    }

    /** P · i / (1 − (1 + i)^−n) */
    private static BigDecimal installment(BigDecimal principal, int months, BigDecimal monthlyRate) {
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, PRECISION);
        BigDecimal discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth, PRECISION));
        return principal.multiply(monthlyRate, PRECISION).divide(discount, PRECISION).setScale(2, RoundingMode.HALF_UP);
    }
}
