package com.example.backend.repository.auth;

import com.example.backend.model.auth.RefreshToken;
import com.example.backend.model.user.User;
import com.example.backend.persistence.BaseRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends BaseRepository<RefreshToken> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from RefreshToken token where token.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
