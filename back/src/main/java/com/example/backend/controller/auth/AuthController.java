package com.example.backend.controller.auth;

import com.example.backend.config.auth.AuthProperties;
import com.example.backend.dto.auth.LogtoSignInRequest;
import com.example.backend.dto.auth.RegisterRequest;
import com.example.backend.dto.auth.TokenResponse;
import com.example.backend.service.auth.AuthService;
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
    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/logto")
    public ResponseEntity<TokenResponse> signInWithLogto(@Valid @RequestBody LogtoSignInRequest request) {
        return withRefreshCookie(authService.signInWithLogto(request.idToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(authService.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${auth.refresh-cookie.name}", required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${auth.refresh-cookie.name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), properties.jwt().refreshTokenTtl()).toString())
                .body(tokens.accessToken());
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
