package com.example.backend.notification.infrastructure.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanOffer;
import com.example.backend.loan.domain.model.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoanDecisionNotifierTest {

    @Test
    void approvalMessageStatesRateInstallmentAndTotalsInPesos() {
        LoanOffer offer = new LoanOffer(new BigDecimal("24.50"), new BigDecimal("79312.45"),
                new BigDecimal("1903498.80"), new BigDecimal("403498.80"));
        Loan loan = loan(LoanStatus.APPROVED, "Bienvenido", offer);

        String message = LoanDecisionNotifier.approvedMessage(loan);

        assertThat(message)
                .contains("$ 1.500.000", "24 meses", "24,5 % EA", "24 cuotas mensuales de $ 79.312,45",
                        "total de $ 1.903.498,80", "intereses: $ 403.498,80", "Mensaje del asesor: Bienvenido");
    }

    @Test
    void rejectionMessageIncludesTheReason() {
        assertThat(LoanDecisionNotifier.rejectedMessage(loan(LoanStatus.REJECTED, "Ingresos insuficientes", null)))
                .isEqualTo("Tu solicitud por $ 1.500.000 a 24 meses fue rechazada. Motivo: Ingresos insuficientes");
    }

    private static Loan loan(LoanStatus status, String comment, LoanOffer offer) {
        return Loan.restore(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1500000"), 24, status, comment,
                UUID.randomUUID(), Instant.now(), offer, 1L, Instant.now(), Instant.now());
    }
}
