package com.example.backend.auth.domain.exception;

import java.time.Duration;

/** Too many failed sign-ins for an account or address; the client may retry after the given delay. */
public class TooManyAttemptsException extends RuntimeException {
    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many failed sign-in attempts. Try again in a few minutes.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() { return retryAfter; }
}
