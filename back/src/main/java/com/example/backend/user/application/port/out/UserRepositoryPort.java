package com.example.backend.user.application.port.out;

import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import com.example.backend.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByLogtoSubject(String logtoSubject);

    /** Newest first. A blank {@code search} lists everyone; otherwise it matches email or any name field. */
    PageResult<User> search(String search, PageQuery page);

    User save(User user);

    void deleteById(UUID id);
}
