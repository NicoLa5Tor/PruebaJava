package com.example.backend.service.user;

import com.example.backend.dto.common.PageResponse;
import com.example.backend.dto.user.CreateUserRequest;
import com.example.backend.dto.user.UpdateProfileRequest;
import com.example.backend.dto.user.UpdateUserRequest;
import com.example.backend.dto.user.UserResponse;
import com.example.backend.exception.BusinessRuleException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.user.User;
import com.example.backend.model.user.UserRole;
import com.example.backend.model.user.UserStatus;
import com.example.backend.repository.auth.RefreshTokenRepository;
import com.example.backend.repository.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return findAll(null, pageable);
    }

    public PageResponse<UserResponse> findAll(String search, Pageable pageable) {
        String term = normalizeOptional(search);
        var page = term == null
                ? userRepository.findAll(pageable)
                : userRepository.search("%" + term.toLowerCase(Locale.ROOT) + "%", pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public UserResponse findById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        String logtoSubject = normalizeOptional(request.logtoSubject());
        ensureEmailAvailable(email, null);
        ensureLogtoSubjectAvailable(logtoSubject, null);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(normalizeOptional(request.firstName()));
        user.setLastName(normalizeOptional(request.lastName()));
        user.setDisplayName(normalizeOptional(request.displayName()));
        user.setAvatarUrl(normalizeOptional(request.avatarUrl()));
        user.setLogtoSubject(logtoSubject);
        user.setRoles(request.roles());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = findEntity(id);
        user.setFirstName(normalizeOptional(request.firstName()));
        user.setLastName(normalizeOptional(request.lastName()));
        user.setDisplayName(normalizeOptional(request.displayName()));
        user.setAvatarUrl(normalizeOptional(request.avatarUrl()));
        return toResponse(userRepository.save(user));
    }

    /** Admin update. {@code actingUserId} prevents an admin from locking themselves out. */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            if (request.roles() != null && !request.roles().contains(UserRole.ADMIN)) {
                throw new BusinessRuleException("You cannot remove your own administrator role.");
            }
            if (request.status() == UserStatus.INACTIVE) {
                throw new BusinessRuleException("You cannot deactivate your own account.");
            }
        }
        return update(id, request);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findEntity(id);

        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            ensureEmailAvailable(email, id);
            user.setEmail(email);
        }
        // null means "leave unchanged"; a blank string clears the field.
        if (request.firstName() != null) {
            user.setFirstName(normalizeOptional(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(normalizeOptional(request.lastName()));
        }
        if (request.displayName() != null) {
            user.setDisplayName(normalizeOptional(request.displayName()));
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalizeOptional(request.avatarUrl()));
        }
        if (request.logtoSubject() != null) {
            String logtoSubject = normalizeOptional(request.logtoSubject());
            ensureLogtoSubjectAvailable(logtoSubject, id);
            user.setLogtoSubject(logtoSubject);
        }
        if (request.emailVerified() != null) {
            user.setEmailVerified(request.emailVerified());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.roles() != null) {
            user.setRoles(request.roles());
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            throw new BusinessRuleException("You cannot delete your own account.");
        }
        delete(id);
    }

    @Transactional
    public void delete(UUID id) {
        User user = findEntity(id);
        refreshTokenRepository.deleteAllByUser(user);
        userRepository.delete(user);
    }

    private User findEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id %s was not found.".formatted(id)));
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new ConflictException("A user with this email already exists.");
                });
    }

    private void ensureLogtoSubjectAvailable(String logtoSubject, UUID currentUserId) {
        if (logtoSubject == null || logtoSubject.isBlank()) {
            return;
        }

        userRepository.findByLogtoSubject(logtoSubject)
                .filter(user -> !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new ConflictException("A user with this Logto subject already exists.");
                });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getLogtoSubject(),
                user.isEmailVerified(),
                user.getStatus(),
                user.getRoles(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
