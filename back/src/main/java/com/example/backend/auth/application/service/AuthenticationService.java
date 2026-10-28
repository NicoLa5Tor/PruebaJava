package com.example.backend.auth.application.service;

import com.example.backend.auth.application.port.in.AuthenticationUseCase;
import com.example.backend.auth.application.port.out.AccessTokenIssuerPort;
import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort;
import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort.ExternalIdentity;
import com.example.backend.auth.application.port.out.RefreshTokenRepositoryPort;
import com.example.backend.auth.domain.exception.AuthenticationException;
import com.example.backend.auth.domain.exception.RegistrationRequiredException;
import com.example.backend.auth.domain.model.RefreshToken;
import com.example.backend.shared.domain.exception.ConflictException;
import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticationService implements AuthenticationUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final ExternalIdentityVerifierPort identityVerifier;
    private final AccessTokenIssuerPort tokenIssuer;
    private final Clock clock;

    public AuthenticationService(UserRepositoryPort users, PasswordHasherPort passwordHasher,
                                 RefreshTokenRepositoryPort refreshTokens, ExternalIdentityVerifierPort identityVerifier,
                                 AccessTokenIssuerPort tokenIssuer, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.identityVerifier = identityVerifier;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    public IssuedTokens signInWithPassword(String email, String password) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        // Always hash, even for unknown emails, so timing does not reveal which accounts exist.
        boolean matches = passwordHasher.matches(password, found.map(User::getPasswordHash).orElse(null));
        User user = found.filter(candidate -> matches)
                .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS));
        ensureActive(user);
        return issueTokens(user);
    }

    @Override
    public IssuedTokens signInWithLogto(String idToken) {
        ExternalIdentity identity = identityVerifier.verify(idToken);
        User user = findExistingUser(identity).orElseThrow(() -> new RegistrationRequiredException(
                identity.email(), identity.firstName(), identity.lastName(), identity.displayName()));
        ensureActive(user);
        return issueTokens(user);
    }

    @Override
    public IssuedTokens register(RegisterCommand command) {
        ExternalIdentity identity = identityVerifier.verify(command.idToken());
        if (findExistingUser(identity).isPresent()) {
            throw new ConflictException("An account already exists for this identity. Sign in instead.");
        }
        return issueTokens(createUser(identity, command));
    }

    @Override
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationException("The refresh token is invalid or expired.");
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.isUsableAt(now))
                .orElseThrow(() -> new AuthenticationException("The refresh token is invalid or expired."));
        current.revoke(now);
        refreshTokens.save(current);
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new AuthenticationException("The refresh token is invalid or expired."));
        ensureActive(user);
        return issueTokens(user);
    }

    @Override
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokens.save(token);
                });
    }

    private Optional<User> findExistingUser(ExternalIdentity identity) {
        return users.findByLogtoSubject(identity.subject())
                .or(() -> users.findByEmail(User.normalizeEmail(identity.email()))
                        .map(existing -> linkExistingUser(existing, identity)));
    }

    private User linkExistingUser(User user, ExternalIdentity identity) {
        if (user.getLogtoSubject() != null && !user.getLogtoSubject().equals(identity.subject())) {
            throw new AuthenticationException("This email is already linked to a different identity.");
        }
        user.setLogtoSubject(identity.subject());
        user.setEmailVerified(true);
        return users.save(user);
    }

    private User createUser(ExternalIdentity identity, RegisterCommand command) {
        User user = User.newUser(identity.email());
        user.setLogtoSubject(identity.subject());
        user.setEmailVerified(true);
        user.setFirstName(blankToNull(command.firstName()));
        user.setLastName(blankToNull(command.lastName()));
        user.setDisplayName(blankToNull(command.displayName()));
        user.setAvatarUrl(identity.avatarUrl());
        user.setRoles(Set.of(UserRole.USER));
        return users.save(user);
    }

    private void ensureActive(User user) {
        if (!user.isActive()) {
            throw new AuthenticationException("This user is inactive.");
        }
    }

    private IssuedTokens issueTokens(User user) {
        String rawRefreshToken = randomToken();
        refreshTokens.save(RefreshToken.issue(user.getId(), hash(rawRefreshToken),
                clock.instant().plus(tokenIssuer.refreshTokenTtl())));
        return new IssuedTokens(tokenIssuer.issue(user), rawRefreshToken);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String randomToken() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }
}
