package com.example.backend.auth.application.port.out;

/** Verifies an ID token from the external identity provider (Logto) and returns the identity it vouches for. */
public interface ExternalIdentityVerifierPort {

    ExternalIdentity verify(String idToken);

    record ExternalIdentity(String subject, String email, String firstName, String lastName, String displayName,
                            String avatarUrl) {
    }
}
