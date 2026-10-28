package com.example.backend.exception;
import com.example.backend.exception.auth.AuthenticationException;
import com.example.backend.exception.auth.RegistrationRequiredException;

import com.example.backend.dto.common.FieldViolationDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "Authentication failed", exception.getMessage(), request));
    }

    @ExceptionHandler(RegistrationRequiredException.class)
    ResponseEntity<ProblemDetail> handleRegistrationRequired(RegistrationRequiredException exception, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.NOT_FOUND, "Registration required", exception.getMessage(), request);
        detail.setProperty("code", "REGISTRATION_REQUIRED");
        detail.setProperty("email", exception.getEmail());
        detail.setProperty("firstName", exception.getFirstName());
        detail.setProperty("lastName", exception.getLastName());
        detail.setProperty("displayName", exception.getDisplayName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to perform this action.", request));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), request));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), request));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolationDto> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.", request);
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolationDto> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolationDto(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more constraints are invalid.", request);
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.", request));
    }

    private FieldViolationDto toFieldViolation(FieldError error) {
        return new FieldViolationDto(error.getField(), error.getDefaultMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
