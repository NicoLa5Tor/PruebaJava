package com.example.backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.application.port.in.AuthenticationUseCase.IssuedTokens;
import com.example.backend.auth.application.port.out.AccessTokenIssuerPort;
import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort;
import com.example.backend.auth.application.port.out.RefreshTokenRepositoryPort;
import com.example.backend.auth.domain.exception.AuthenticationException;
import com.example.backend.auth.domain.model.AccessToken;
import com.example.backend.auth.domain.model.RefreshToken;
import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    @Mock private UserRepositoryPort users;
    @Mock private PasswordHasherPort passwordHasher;
    @Mock private RefreshTokenRepositoryPort refreshTokens;
    @Mock private ExternalIdentityVerifierPort identityVerifier;
    @Mock private AccessTokenIssuerPort tokenIssuer;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(users, passwordHasher, refreshTokens, identityVerifier, tokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(tokenIssuer.issue(any())).thenReturn(new AccessToken("access", 900));
        lenient().when(tokenIssuer.refreshTokenTtl()).thenReturn(Duration.ofDays(14));
    }

    @Test
    void correctPasswordIssuesTokensForTheNormalizedEmail() {
        User user = user(UserStatus.ACTIVE);
        when(users.findByEmail("usuario@test.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("123", "hash")).thenReturn(true);

        IssuedTokens tokens = service.signInWithPassword("  Usuario@Test.com ", "123");

        assertThat(tokens.accessToken().value()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isNotBlank();
        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(stored.capture());
        assertThat(stored.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(AuthenticationService.hash(tokens.refreshToken()));
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void wrongPasswordIsRejectedWithAGenericMessage() {
        when(users.findByEmail("usuario@test.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordHasher.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.signInWithPassword("usuario@test.com", "bad"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage(AuthenticationService.INVALID_CREDENTIALS);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unknownEmailStillHashesAndGetsTheSameMessage() {
        when(users.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signInWithPassword("nobody@test.com", "123"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage(AuthenticationService.INVALID_CREDENTIALS);
        verify(passwordHasher).matches(eq("123"), isNull());
    }

    @Test
    void inactiveUserCannotSignIn() {
        when(users.findByEmail("usuario@test.com")).thenReturn(Optional.of(user(UserStatus.INACTIVE)));
        when(passwordHasher.matches("123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.signInWithPassword("usuario@test.com", "123"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void refreshRevokesThePresentedTokenAndIssuesANewOne() {
        User user = user(UserStatus.ACTIVE);
        RefreshToken current = new RefreshToken(UUID.randomUUID(), user.getId(), AuthenticationService.hash("raw"),
                NOW.plusSeconds(60), null);
        when(refreshTokens.findByTokenHash(AuthenticationService.hash("raw"))).thenReturn(Optional.of(current));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        IssuedTokens tokens = service.refresh("raw");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(tokens.refreshToken()).isNotEqualTo("raw");
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        RefreshToken expired = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), AuthenticationService.hash("raw"),
                NOW.minusSeconds(1), null);
        when(refreshTokens.findByTokenHash(AuthenticationService.hash("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationException.class);
    }

    private static User user(UserStatus status) {
        User user = new User(UUID.randomUUID(), NOW, NOW);
        user.setEmail("usuario@test.com");
        user.setPasswordHash("hash");
        user.setStatus(status);
        return user;
    }
}
