package com.example.backend.loan.infrastructure.events;

import com.example.backend.loan.application.port.out.LoanEventPublisherPort;
import com.example.backend.loan.domain.event.LoanStatusChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Hands the event to Spring; {@link LoanEventStream} receives it only after the transaction commits. */
@Component
class SpringLoanEventPublisher implements LoanEventPublisherPort {

    private final ApplicationEventPublisher publisher;

    SpringLoanEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(LoanStatusChanged event) {
        publisher.publishEvent(event);
    }
}
