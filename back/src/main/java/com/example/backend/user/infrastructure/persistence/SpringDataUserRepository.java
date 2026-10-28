package com.example.backend.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmailIgnoreCase(String email);

    Optional<UserJpaEntity> findByLogtoSubject(String logtoSubject);

    @Query("""
            select user from User user
            where lower(user.email) like :pattern
               or lower(coalesce(user.displayName, '')) like :pattern
               or lower(coalesce(user.firstName, '')) like :pattern
               or lower(coalesce(user.lastName, '')) like :pattern
            """)
    Page<UserJpaEntity> search(@Param("pattern") String pattern, Pageable pageable);
}
