package com.example.backend.user.application.port.out;

public interface PasswordHasherPort {

    String hash(String rawPassword);

    /** False for a null hash too, but still spends the hashing time so unknown accounts are not distinguishable. */
    boolean matches(String rawPassword, String passwordHash);
}
