package com.example.backend.user.application.service;

import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import com.example.backend.user.application.port.in.UserQueryUseCase;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService implements UserQueryUseCase {

    private final UserRepositoryPort users;

    public UserQueryService(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public User findById(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id %s was not found.".formatted(id)));
    }

    @Override
    public PageResult<User> search(String search, PageQuery page) {
        return users.search(search == null || search.isBlank() ? null : search.trim(), page);
    }
}
