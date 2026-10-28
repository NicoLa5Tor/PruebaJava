package com.example.backend.notification.infrastructure.loan;

import com.example.backend.loan.application.port.out.ApplicantNotifierPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanOffer;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.notification.application.port.in.NotificationUseCase;
import com.example.backend.notification.domain.model.NotificationType;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Bridges the loan module to the inbox: turns a review into the Spanish message the applicant reads. Runs inside
 * the review transaction, so a loan is never approved without its notification (or vice versa).
 */
@Component
class LoanDecisionNotifier implements ApplicantNotifierPort {
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final NotificationUseCase notifications;

    LoanDecisionNotifier(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void notifyDecision(Loan loan) {
        if (loan.status() == LoanStatus.APPROVED) {
            notifications.send(loan.applicantId(), loan.id(), NotificationType.LOAN_APPROVED,
                    "Tu préstamo fue aprobado", approvedMessage(loan));
        } else if (loan.status() == LoanStatus.REJECTED) {
            notifications.send(loan.applicantId(), loan.id(), NotificationType.LOAN_REJECTED,
                    "Tu solicitud de préstamo fue rechazada", rejectedMessage(loan));
        }
    }

    static String approvedMessage(Loan loan) {
        LoanOffer offer = loan.offer();
        StringBuilder text = new StringBuilder()
                .append("Tu préstamo por ").append(money(loan.amount()))
                .append(" a ").append(loan.termMonths()).append(" meses fue aprobado con una tasa del ")
                .append(percent(offer.annualRate())).append(" EA. ")
                .append("Pagarás ").append(loan.termMonths()).append(" cuotas mensuales de ")
                .append(money(offer.monthlyPayment())).append(", para un total de ")
                .append(money(offer.totalPayable())).append(" (intereses: ")
                .append(money(offer.totalInterest())).append(").");
        if (loan.reviewComment() != null) {
            text.append(" Mensaje del asesor: ").append(loan.reviewComment());
        }
        return text.toString();
    }

    static String rejectedMessage(Loan loan) {
        String base = "Tu solicitud por " + money(loan.amount()) + " a " + loan.termMonths() + " meses fue rechazada.";
        return loan.reviewComment() == null ? base : base + " Motivo: " + loan.reviewComment();
    }

    /** "$ 1.500.000" or "$ 79.312,45": cents only when there are any. */
    static String money(BigDecimal amount) {
        int decimals = amount.stripTrailingZeros().scale() > 0 ? 2 : 0;
        NumberFormat format = NumberFormat.getCurrencyInstance(COLOMBIA);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(amount).replace(' ', ' ');
    }

    static String percent(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }
}
