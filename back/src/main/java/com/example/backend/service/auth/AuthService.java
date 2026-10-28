package com.example.backend.service.auth;

import com.example.backend.config.auth.AuthProperties;
import com.example.backend.dto.auth.RegisterRequest;
import com.example.backend.dto.auth.TokenResponse;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.auth.AuthenticationException;
import com.example.backend.exception.auth.RegistrationRequiredException;
import com.example.backend.model.auth.RefreshToken;
import com.example.backend.model.user.User;
import com.example.backend.model.user.UserRole;
import com.example.backend.model.user.UserStatus;
import com.example.backend.repository.auth.RefreshTokenRepository;
import com.example.backend.repository.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LogtoIdTokenService logtoIdTokenService;
    private final LocalJwtService localJwtService;
    private final AuthProperties properties;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       LogtoIdTokenService logtoIdTokenService, LocalJwtService localJwtService,
                       AuthProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.logtoIdTokenService = logtoIdTokenService;
        this.localJwtService = localJwtService;
        this.properties = properties;
    }

    /** Signs in an existing account. Unknown identities are sent to {@link #register} instead of being auto-created. */
    public IssuedTokens signInWithLogto(String idToken) {
        LogtoIdTokenService.LogtoIdentity identity = logtoIdTokenService.verify(idToken);
        User user = findExistingUser(identity).orElseThrow(() -> new RegistrationRequiredException(
                identity.email(), identity.firstName(), identity.lastName(), identity.displayName()));
        ensureActive(user);
        return issueTokens(user);
    }

    public IssuedTokens register(RegisterRequest request) {
        LogtoIdTokenService.LogtoIdentity identity = logtoIdTokenService.verify(request.idToken());
        if (findExistingUser(identity).isPresent()) {
            throw new ConflictException("An account already exists for this identity. Sign in instead.");
        }
        return issueTokens(createUser(identity, request));
    }

    private Optional<User> findExistingUser(LogtoIdTokenService.LogtoIdentity identity) {
        return userRepository.findByLogtoSubject(identity.subject())
                .or(() -> userRepository.findByEmailIgnoreCase(identity.email())
                        .map(existing -> linkExistingUser(existing, identity)));
    }

    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationException("The refresh token is invalid or expired.");
        }
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null && token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AuthenticationException("The refresh token is invalid or expired."));
        current.setRevokedAt(Instant.now());
        ensureActive(current.getUser());
        return issueTokens(current.getUser());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private User linkExistingUser(User user, LogtoIdTokenService.LogtoIdentity identity) {
        if (user.getLogtoSubject() != null && !user.getLogtoSubject().equals(identity.subject())) {
            throw new AuthenticationException("This email is already linked to a different identity.");
        }
        user.setLogtoSubject(identity.subject());
        user.setEmailVerified(true);
        return user;
    }

    private User createUser(LogtoIdTokenService.LogtoIdentity identity, RegisterRequest request) {
        User user = new User();
        user.setEmail(identity.email().trim().toLowerCase(java.util.Locale.ROOT));
        user.setLogtoSubject(identity.subject());
        user.setEmailVerified(true);
        user.setFirstName(blankToNull(request.firstName()));
        user.setLastName(blankToNull(request.lastName()));
        user.setDisplayName(blankToNull(request.displayName()));
        user.setAvatarUrl(identity.avatarUrl());
        user.setRoles(Set.of(UserRole.USER));
        return userRepository.save(user);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("This user is inactive.");
        }
    }

    private IssuedTokens issueTokens(User user) {
        String rawRefreshToken = randomToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);
        return new IssuedTokens(
                new TokenResponse(localJwtService.create(user), "Bearer", localJwtService.accessTokenExpiresIn()),
                rawRefreshToken
        );
    }

    /** The refresh token never leaves the server in a response body; the controller writes it to an HttpOnly cookie. */
    public record IssuedTokens(TokenResponse accessToken, String refreshToken) {
    }

    private String randomToken() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }
}
