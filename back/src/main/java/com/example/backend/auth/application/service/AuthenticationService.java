package com.example.backend.auth.application.service;

import com.example.backend.auth.application.port.in.AuthenticationUseCase;
import com.example.backend.auth.application.port.out.AccessTokenIssuerPort;
import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort;
import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort.ExternalIdentity;
import com.example.backend.auth.application.port.out.HumanVerificationPort;
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
    static final String HUMAN_CHECK_FAILED = "The security check failed. Please try again.";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final ExternalIdentityVerifierPort identityVerifier;
    private final AccessTokenIssuerPort tokenIssuer;
    private final HumanVerificationPort humanVerification;
    private final LoginThrottle throttle;
    private final Clock clock;

    public AuthenticationService(UserRepositoryPort users, PasswordHasherPort passwordHasher,
                                 RefreshTokenRepositoryPort refreshTokens, ExternalIdentityVerifierPort identityVerifier,
                                 AccessTokenIssuerPort tokenIssuer, HumanVerificationPort humanVerification,
                                 LoginThrottle throttle, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.identityVerifier = identityVerifier;
        this.tokenIssuer = tokenIssuer;
        this.humanVerification = humanVerification;
        this.throttle = throttle;
        this.clock = clock;
    }

    /**
     * Order matters: the throttle runs first so a locked-out attacker cannot even spend Turnstile verifications,
     * then the bot check, then the password. Failures count towards the limits; a success clears the account's.
     */
    @Override
    public IssuedTokens signInWithPassword(PasswordSignInCommand command) {
        String email = User.normalizeEmail(command.email());
        throttle.checkAllowed(email, command.clientIp());
        if (humanVerification.isEnabled() && !humanVerification.verify(command.humanToken(), command.clientIp())) {
            throw new AuthenticationException(HUMAN_CHECK_FAILED);
        }
        Optional<User> found = users.findByEmail(email);
        // Always hash, even for unknown emails, so timing does not reveal which accounts exist.
        boolean matches = passwordHasher.matches(command.password(), found.map(User::getPasswordHash).orElse(null));
        if (found.isEmpty() || !matches) {
            throttle.recordFailure(email, command.clientIp());
            throw new AuthenticationException(INVALID_CREDENTIALS);
        }
        User user = found.get();
        ensureActive(user);
        throttle.recordSuccess(email);
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
