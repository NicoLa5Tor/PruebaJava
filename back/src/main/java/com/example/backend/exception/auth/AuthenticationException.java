package com.example.backend.exception.auth;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
}
