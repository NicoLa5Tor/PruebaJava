package com.example.backend.user.infrastructure.security;

import com.example.backend.user.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Compared against when the account has no password, so the response time does not reveal it. */
    private final String dummyHash = encoder.encode("dummy-password-for-timing");

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null) {
            return false;
        }
        if (passwordHash == null) {
            encoder.matches(rawPassword, dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
