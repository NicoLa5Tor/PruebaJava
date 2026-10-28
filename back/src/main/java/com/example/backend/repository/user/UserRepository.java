package com.example.backend.repository.user;

import com.example.backend.model.user.User;
import com.example.backend.persistence.BaseRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends BaseRepository<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByLogtoSubject(String logtoSubject);

    @Query("""
            select user from User user
            where lower(user.email) like :pattern
               or lower(coalesce(user.displayName, '')) like :pattern
               or lower(coalesce(user.firstName, '')) like :pattern
               or lower(coalesce(user.lastName, '')) like :pattern
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
