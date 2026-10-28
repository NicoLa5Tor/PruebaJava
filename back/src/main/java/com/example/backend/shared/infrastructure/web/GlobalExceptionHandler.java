package com.example.backend.shared.infrastructure.web;

import com.example.backend.shared.domain.exception.BusinessRuleException;
import com.example.backend.shared.domain.exception.ConflictException;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into {@code application/problem+json}. Framework errors (malformed JSON, unknown routes,
 * wrong method...) are handled by {@link ResponseEntityExceptionHandler}; domain errors map to 404/409/422.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrentUpdate(RuntimeException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Concurrent update",
                "The resource was modified by another request. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        LOGGER.warn("Data integrity violation on {}: {}", request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "Conflict",
                "The operation conflicts with related records. A user with loans should be deactivated instead of deleted.",
                request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", exception.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolationDto> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolationDto(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        ProblemDetail detail = Problems.of(HttpStatus.BAD_REQUEST, "Validation failed", "One or more constraints are invalid.", request);
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "The value of '%s' is not valid.".formatted(exception.getName()), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error on {}", request.getRequestURI(), exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolationDto> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        ProblemDetail detail = exception.getBody();
        detail.setTitle("Validation failed");
        detail.setDetail("One or more fields are invalid.");
        detail.setProperty("errors", errors);
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    /** Adds the timestamp to the problem bodies Spring MVC builds for framework exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp"))) {
            problem.setProperty("timestamp", Instant.now());
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String title, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Problems.of(status, title, detail, request));
    }

    private FieldViolationDto toFieldViolation(FieldError error) {
        return new FieldViolationDto(error.getField(), error.getDefaultMessage());
    }
}
