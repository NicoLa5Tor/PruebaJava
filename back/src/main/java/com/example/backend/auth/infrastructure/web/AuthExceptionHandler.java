package com.example.backend.auth.infrastructure.web;

import com.example.backend.auth.domain.exception.AuthenticationException;
import com.example.backend.auth.domain.exception.RegistrationRequiredException;
import com.example.backend.auth.domain.exception.TooManyAttemptsException;
import com.example.backend.shared.infrastructure.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before the global handler, whose catch-all would otherwise turn these into 500s. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Problems.of(HttpStatus.UNAUTHORIZED, "Authentication failed", exception.getMessage(), request));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException exception, HttpServletRequest request) {
        long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        ProblemDetail detail = Problems.of(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", exception.getMessage(), request);
        detail.setProperty("retryAfterSeconds", retryAfterSeconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(detail);
    }

    @ExceptionHandler(RegistrationRequiredException.class)
    ResponseEntity<ProblemDetail> handleRegistrationRequired(RegistrationRequiredException exception, HttpServletRequest request) {
        ProblemDetail detail = Problems.of(HttpStatus.NOT_FOUND, "Registration required", exception.getMessage(), request);
        detail.setProperty("code", "REGISTRATION_REQUIRED");
        detail.setProperty("email", exception.getEmail());
        detail.setProperty("firstName", exception.getFirstName());
        detail.setProperty("lastName", exception.getLastName());
        detail.setProperty("displayName", exception.getDisplayName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }
}
