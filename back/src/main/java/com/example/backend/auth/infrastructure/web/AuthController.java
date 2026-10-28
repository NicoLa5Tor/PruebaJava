package com.example.backend.auth.infrastructure.web;

import com.example.backend.auth.application.port.in.AuthenticationUseCase;
import com.example.backend.auth.application.port.in.AuthenticationUseCase.IssuedTokens;
import com.example.backend.auth.infrastructure.config.AuthProperties;
import com.example.backend.auth.infrastructure.web.dto.AuthDtos.LogtoSignInRequest;
import com.example.backend.auth.infrastructure.web.dto.AuthDtos.PasswordLoginRequest;
import com.example.backend.auth.infrastructure.web.dto.AuthDtos.RegisterRequest;
import com.example.backend.auth.infrastructure.web.dto.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthenticationUseCase authentication;
    private final AuthProperties properties;

    public AuthController(AuthenticationUseCase authentication, AuthProperties properties) {
        this.authentication = authentication;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> signInWithPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return withRefreshCookie(authentication.signInWithPassword(request.email(), request.password()));
    }

    @PostMapping("/logto")
    public ResponseEntity<TokenResponse> signInWithLogto(@Valid @RequestBody LogtoSignInRequest request) {
        return withRefreshCookie(authentication.signInWithLogto(request.idToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(authentication.register(request.toCommand()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${auth.refresh-cookie.name}", required = false) String refreshToken) {
        return withRefreshCookie(authentication.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${auth.refresh-cookie.name}", required = false) String refreshToken) {
        authentication.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), properties.jwt().refreshTokenTtl()).toString())
                .body(TokenResponse.from(tokens.accessToken()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        AuthProperties.RefreshCookie cookie = properties.refreshCookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Strict")
                .path(cookie.path())
                .maxAge(maxAge)
                .build();
    }
}
