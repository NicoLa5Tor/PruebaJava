package com.example.backend.loan.infrastructure.web.dto;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.domain.model.LoanView;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** HTTP contract of /api/v1/loans. The limits mirror the domain rules in {@link Loan}. */
public final class LoanDtos {

    private LoanDtos() {
    }

    public record LoanRequest(
            @NotNull @DecimalMin("100") @DecimalMax("100000000") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull @Min(1) @Max(120) Integer termMonths
    ) {
    }

    public record RejectLoanRequest(@Size(max = 500) String comment) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoanResponse(
            UUID id,
            UUID applicantId,
            String applicantEmail,
            String applicantName,
            BigDecimal amount,
            int termMonths,
            LoanStatus status,
            String reviewComment,
            UUID reviewedBy,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static LoanResponse from(Loan loan) {
            return of(loan, null, null);
        }

        public static LoanResponse from(LoanView view) {
            return of(view.loan(), view.applicantEmail(), view.applicantName());
        }

        private static LoanResponse of(Loan loan, String applicantEmail, String applicantName) {
            return new LoanResponse(loan.id(), loan.applicantId(), applicantEmail, applicantName, loan.amount(),
                    loan.termMonths(), loan.status(), loan.reviewComment(), loan.reviewedBy(), loan.reviewedAt(),
                    loan.createdAt(), loan.updatedAt());
        }
    }
}
