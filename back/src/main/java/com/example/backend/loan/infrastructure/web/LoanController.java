package com.example.backend.loan.infrastructure.web;

import com.example.backend.loan.application.port.in.LoanQueryUseCase;
import com.example.backend.loan.application.port.in.RequestLoanUseCase;
import com.example.backend.loan.application.port.in.ReviewLoanUseCase;
import com.example.backend.loan.domain.event.LoanStatusChanged;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.infrastructure.events.LoanEventStream;
import com.example.backend.loan.infrastructure.web.dto.LoanDtos.LoanRequest;
import com.example.backend.loan.infrastructure.web.dto.LoanDtos.LoanResponse;
import com.example.backend.loan.infrastructure.web.dto.LoanDtos.RejectLoanRequest;
import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.infrastructure.security.AuthenticatedUser;
import com.example.backend.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans")
public class LoanController {
    private static final String REVIEWER = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";
    private static final Duration HEARTBEAT = Duration.ofSeconds(20);

    private final RequestLoanUseCase requests;
    private final ReviewLoanUseCase reviews;
    private final LoanQueryUseCase queries;
    private final LoanEventStream events;

    public LoanController(RequestLoanUseCase requests, ReviewLoanUseCase reviews, LoanQueryUseCase queries,
                          LoanEventStream events) {
        this.requests = requests;
        this.reviews = reviews;
        this.queries = queries;
        this.events = events;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Request a loan (starts as PENDING)")
    public ResponseEntity<LoanResponse> request(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody LoanRequest body) {
        LoanResponse loan = LoanResponse.from(requests.request(principal.userId(), body.amount(), body.termMonths()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(loan.id())
                .toUri();
        return ResponseEntity.created(location).body(loan);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "The caller's loans, newest first (cached)")
    public List<LoanResponse> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queries.findMine(principal.userId()).stream().map(LoanResponse::from).toList();
    }

    @GetMapping(path = "/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Server-sent events with status changes of the caller's loans")
    public Flux<ServerSentEvent<LoanStatusChanged>> myEvents(@AuthenticationPrincipal AuthenticatedUser principal) {
        Flux<ServerSentEvent<LoanStatusChanged>> changes = events.forApplicant(principal.userId())
                .map(event -> ServerSentEvent.builder(event).id(event.loanId().toString()).event("loan-status").build());
        // Comments keep proxies from closing an idle connection.
        Flux<ServerSentEvent<LoanStatusChanged>> heartbeats = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<LoanStatusChanged>builder().comment("keep-alive").build());
        return Flux.merge(changes, heartbeats);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "One loan: applicants see their own, reviewers see any")
    public LoanResponse findById(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        boolean reviewer = principal.hasAnyRole("ADMIN", "SUPER_ADMIN");
        return LoanResponse.from(queries.findById(id, principal.userId(), reviewer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Change amount or term of an own PENDING request")
    public LoanResponse modify(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id,
                               @Valid @RequestBody LoanRequest body) {
        return LoanResponse.from(requests.modify(id, principal.userId(), body.amount(), body.termMonths()));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Withdraw an own PENDING request (loans are never deleted)")
    public LoanResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return LoanResponse.from(requests.cancel(id, principal.userId()));
    }

    @GetMapping
    @PreAuthorize(REVIEWER)
    @Operation(summary = "Review queue for admins, optionally filtered by status")
    public PageResponse<LoanResponse> search(@RequestParam(required = false) LoanStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(queries.search(status, PageQuery.of(page, size)), LoanResponse::from);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize(REVIEWER)
    @Operation(summary = "Approve a PENDING loan")
    public LoanResponse approve(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return LoanResponse.from(reviews.approve(id, principal.userId()));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize(REVIEWER)
    @Operation(summary = "Reject a PENDING loan with an optional comment")
    public LoanResponse reject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id,
                               @Valid @RequestBody(required = false) RejectLoanRequest body) {
        return LoanResponse.from(reviews.reject(id, principal.userId(), body == null ? null : body.comment()));
    }
}
