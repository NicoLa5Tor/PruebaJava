package com.example.backend.auth.application.port.in;

import com.example.backend.auth.domain.model.AccessToken;

public interface AuthenticationUseCase {

    IssuedTokens signInWithPassword(PasswordSignInCommand command);

    /** Signs in an existing account. Unknown identities are sent to {@link #register} instead of being auto-created. */
    IssuedTokens signInWithLogto(String idToken);

    /** New accounts always get only the USER role; the email comes from the verified token. */
    IssuedTokens register(RegisterCommand command);

    /** Rotates the refresh token: the presented one is revoked and a new pair is issued. */
    IssuedTokens refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    /** {@code humanToken} is the Turnstile token; {@code clientIp} feeds the brute-force limits. */
    record PasswordSignInCommand(String email, String password, String humanToken, String clientIp) {
    }

    record RegisterCommand(String idToken, String firstName, String lastName, String displayName) {
    }

    /** The refresh token never leaves the server in a response body; the controller writes it to an HttpOnly cookie. */
    record IssuedTokens(AccessToken accessToken, String refreshToken) {
    }
}
