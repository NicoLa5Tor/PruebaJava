package com.example.backend.service.auth;

import com.example.backend.config.auth.AuthProperties;
import com.example.backend.exception.auth.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LogtoIdTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogtoIdTokenService.class);
    private final AuthProperties properties;
    private final JwtDecoder decoder;

    public LogtoIdTokenService(AuthProperties properties) {
        this.properties = properties;
        NimbusJwtDecoder logtoDecoder = NimbusJwtDecoder.withJwkSetUri(properties.logto().issuer() + "/jwks")
                .jwsAlgorithm(SignatureAlgorithm.ES384)
                .build();
        logtoDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.logto().issuer()));
        this.decoder = logtoDecoder;
    }

    public LogtoIdentity verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!jwt.getAudience().contains(properties.logto().appId())) {
                throw new AuthenticationException("The Logto token was issued for a different application.");
            }
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            if (email == null || !Boolean.TRUE.equals(emailVerified)) {
                throw new AuthenticationException("A verified email is required to sign in.");
            }
            return new LogtoIdentity(
                    jwt.getSubject(), email, jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture")
            );
        } catch (JwtException exception) {
            LOGGER.warn("Logto ID token validation failed: {}", exception.getMessage());
            throw new AuthenticationException("The Logto identity token is invalid or expired.");
        }
    }

    public record LogtoIdentity(String subject, String email, String firstName, String lastName, String displayName, String avatarUrl) {
    }
}
