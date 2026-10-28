package com.example.backend.service.auth;

import com.example.backend.config.auth.AuthProperties;
import com.example.backend.model.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class LocalJwtService {
    private final AuthProperties properties;
    private final SecretKey key;

    public LocalJwtService(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String create(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public LocalPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Object rolesClaim = claims.get("roles");
        java.util.List<String> roles = rolesClaim instanceof java.util.List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : java.util.List.of();
        return new LocalPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class), roles);
    }

    public long accessTokenExpiresIn() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }

    public record LocalPrincipal(UUID userId, String email, java.util.List<String> roles) {
    }
}
