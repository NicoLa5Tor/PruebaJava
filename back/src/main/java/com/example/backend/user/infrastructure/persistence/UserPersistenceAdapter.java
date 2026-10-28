package com.example.backend.user.infrastructure.persistence;

import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByLogtoSubject(String logtoSubject) {
        return repository.findByLogtoSubject(logtoSubject).map(UserJpaEntity::toDomain);
    }

    @Override
    public PageResult<User> search(String search, PageQuery page) {
        PageRequest request = PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserJpaEntity> result = search == null
                ? repository.findAll(request)
                : repository.search("%" + search.toLowerCase(Locale.ROOT) + "%", request);
        return new PageResult<>(result.map(UserJpaEntity::toDomain).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = user.getId() == null
                ? new UserJpaEntity()
                : repository.findById(user.getId()).orElseThrow(() ->
                        new ResourceNotFoundException("User with id %s was not found.".formatted(user.getId())));
        entity.apply(user);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
        // Surface foreign key violations (a user with loans) here instead of at commit time.
        repository.flush();
    }
}
