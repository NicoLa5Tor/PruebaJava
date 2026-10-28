package com.example.backend.auth.infrastructure.logto;

import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort;
import com.example.backend.auth.domain.exception.AuthenticationException;
import com.example.backend.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Checks Logto ID tokens against the issuer's JWKS, audience and verified email. */
@Component
class LogtoIdentityVerifierAdapter implements ExternalIdentityVerifierPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogtoIdentityVerifierAdapter.class);
    private final AuthProperties properties;
    private final JwtDecoder decoder;

    LogtoIdentityVerifierAdapter(AuthProperties properties) {
        this.properties = properties;
        NimbusJwtDecoder logtoDecoder = NimbusJwtDecoder.withJwkSetUri(properties.logto().issuer() + "/jwks")
                .jwsAlgorithm(SignatureAlgorithm.ES384)
                .build();
        logtoDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.logto().issuer()));
        this.decoder = logtoDecoder;
    }

    @Override
    public ExternalIdentity verify(String idToken) {
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
            return new ExternalIdentity(
                    jwt.getSubject(), email, jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture")
            );
        } catch (JwtException exception) {
            LOGGER.warn("Logto ID token validation failed: {}", exception.getMessage());
            throw new AuthenticationException("The Logto identity token is invalid or expired.");
        }
    }
}
