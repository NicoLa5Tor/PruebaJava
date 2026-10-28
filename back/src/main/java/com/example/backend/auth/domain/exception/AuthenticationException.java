package com.example.backend.auth.domain.exception;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
}
