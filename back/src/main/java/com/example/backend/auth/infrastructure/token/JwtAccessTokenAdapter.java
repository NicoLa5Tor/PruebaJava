package com.example.backend.auth.infrastructure.token;

import com.example.backend.auth.application.port.out.AccessTokenIssuerPort;
import com.example.backend.auth.domain.model.AccessToken;
import com.example.backend.auth.infrastructure.config.AuthProperties;
import com.example.backend.shared.infrastructure.security.AuthenticatedUser;
import com.example.backend.user.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** Issues and verifies the API's own short-lived HS256 access tokens. */
@Component
public class JwtAccessTokenAdapter implements AccessTokenIssuerPort {
    private final AuthProperties properties;
    private final SecretKey key;

    public JwtAccessTokenAdapter(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public AccessToken issue(User user) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().accessTokenTtl())))
                .signWith(key)
                .compact();
        return new AccessToken(token, properties.jwt().accessTokenTtl().toSeconds());
    }

    @Override
    public Duration refreshTokenTtl() {
        return properties.jwt().refreshTokenTtl();
    }

    /** Throws {@link io.jsonwebtoken.JwtException} when the signature or expiry is invalid. */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Object rolesClaim = claims.get("roles");
        List<String> roles = rolesClaim instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class), roles);
    }
}
