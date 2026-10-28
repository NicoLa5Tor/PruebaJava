package com.example.backend.auth.application.port.out;

import com.example.backend.auth.domain.model.RefreshToken;
import java.util.Optional;

public interface RefreshTokenRepositoryPort {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);
}
