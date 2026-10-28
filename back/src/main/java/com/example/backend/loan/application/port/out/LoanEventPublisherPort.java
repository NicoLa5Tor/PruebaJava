package com.example.backend.loan.application.port.out;

import com.example.backend.loan.domain.event.LoanStatusChanged;

public interface LoanEventPublisherPort {

    /** Delivery happens only if the current transaction commits. */
    void publish(LoanStatusChanged event);
}
