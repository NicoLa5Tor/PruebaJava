package com.example.backend.auth.infrastructure.persistence;

import com.example.backend.auth.application.port.out.RefreshTokenRepositoryPort;
import com.example.backend.auth.domain.model.RefreshToken;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository repository;

    RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = token.getId() == null
                ? new RefreshTokenJpaEntity()
                : repository.findById(token.getId()).orElseThrow();
        entity.apply(token);
        return repository.save(entity).toDomain();
    }
}
