package com.example.backend.loan.infrastructure.events;

import com.example.backend.loan.domain.event.LoanStatusChanged;
import com.example.backend.loan.domain.model.LoanStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LoanEventStreamTest {

    @Test
    void eachApplicantOnlyReceivesTheirOwnLoanEvents() {
        LoanEventStream stream = new LoanEventStream();
        UUID applicant = UUID.randomUUID();
        LoanStatusChanged mine = event(applicant, LoanStatus.APPROVED);
        LoanStatusChanged someoneElses = event(UUID.randomUUID(), LoanStatus.REJECTED);

        StepVerifier.create(stream.forApplicant(applicant).take(1))
                .then(() -> {
                    stream.onCommitted(someoneElses);
                    stream.onCommitted(mine);
                })
                .expectNext(mine)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    private static LoanStatusChanged event(UUID applicant, LoanStatus status) {
        return new LoanStatusChanged(UUID.randomUUID(), applicant, status, null, Instant.now());
    }
}
