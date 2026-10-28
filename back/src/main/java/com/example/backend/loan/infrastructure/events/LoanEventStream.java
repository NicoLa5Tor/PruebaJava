package com.example.backend.loan.infrastructure.events;

import com.example.backend.loan.domain.event.LoanStatusChanged;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Reactive hub behind the server-sent events endpoint. One hot multicast sink fans committed status changes out
 * to every open connection; each subscriber filters the events of its own loans. Subscribers that cannot keep up
 * are skipped instead of blocking the request thread that committed the review.
 */
@Component
public class LoanEventStream {

    private final Sinks.Many<LoanStatusChanged> sink = Sinks.many().multicast().directBestEffort();

    @TransactionalEventListener
    void onCommitted(LoanStatusChanged event) {
        // Reviews commit on several request threads at once; retry briefly instead of failing on concurrent emits.
        sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
    }

    public Flux<LoanStatusChanged> forApplicant(UUID applicantId) {
        return sink.asFlux().filter(event -> event.applicantId().equals(applicantId));
    }
}
