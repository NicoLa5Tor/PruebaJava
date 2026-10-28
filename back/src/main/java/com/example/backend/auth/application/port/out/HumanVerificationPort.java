package com.example.backend.auth.application.port.out;

/** Bot protection for the password sign-in (Cloudflare Turnstile in production). */
public interface HumanVerificationPort {

    /** Whether a challenge token must accompany every password sign-in. */
    boolean isEnabled();

    /** Verifies a single-use challenge token issued to the visitor's browser. */
    boolean verify(String token, String clientIp);
}
