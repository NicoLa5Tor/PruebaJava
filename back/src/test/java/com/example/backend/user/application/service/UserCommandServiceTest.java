package com.example.backend.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.shared.domain.exception.BusinessRuleException;
import com.example.backend.shared.domain.exception.ConflictException;
import com.example.backend.user.application.port.in.UserCommandUseCase.CreateUserCommand;
import com.example.backend.user.application.port.in.UserCommandUseCase.UpdateUserCommand;
import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {
    private static final UUID SUPER_ADMIN_ID = UUID.randomUUID();

    @Mock private UserRepositoryPort users;
    @Mock private PasswordHasherPort passwordHasher;
    @InjectMocks private UserCommandService service;

    @Test
    void createsAnAdminWithAHashedPassword() {
        when(users.findByEmail("new.admin@test.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("s3cure-pass")).thenReturn("bcrypt-hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.create(new CreateUserCommand(" New.Admin@Test.com ", "Nueva", null, null, null, null,
                Set.of(UserRole.ADMIN), "s3cure-pass"));

        assertThat(created.getEmail()).isEqualTo("new.admin@test.com");
        assertThat(created.getRoles()).containsExactly(UserRole.ADMIN);
        assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void shortPasswordsAreRefused() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateUserCommand("a@test.com", null, null, null, null, null,
                Set.of(UserRole.USER), "123")))
                .isInstanceOf(BusinessRuleException.class);
        verify(users, never()).save(any());
    }

    @Test
    void duplicatedEmailIsAConflict() {
        when(users.findByEmail("taken@test.com")).thenReturn(Optional.of(new User(UUID.randomUUID(), null, null)));

        assertThatThrownBy(() -> service.create(new CreateUserCommand("taken@test.com", null, null, null, null, null,
                null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void superAdminCannotDemoteDeactivateOrDeleteThemselves() {
        UpdateUserCommand demote = new UpdateUserCommand(null, null, null, null, null, null, null, null,
                Set.of(UserRole.ADMIN));
        UpdateUserCommand deactivate = new UpdateUserCommand(null, null, null, null, null, null, null,
                UserStatus.INACTIVE, null);

        assertThatThrownBy(() -> service.update(SUPER_ADMIN_ID, demote, SUPER_ADMIN_ID))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.update(SUPER_ADMIN_ID, deactivate, SUPER_ADMIN_ID))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.delete(SUPER_ADMIN_ID, SUPER_ADMIN_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(users, never()).save(any());
    }

    @Test
    void superAdminCanPromoteAnotherUserToAdmin() {
        UUID target = UUID.randomUUID();
        User user = new User(target, Instant.now(), Instant.now());
        user.setEmail("user@test.com");
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.update(target, new UpdateUserCommand(null, null, null, null, null, null, null, null,
                Set.of(UserRole.ADMIN)), SUPER_ADMIN_ID);

        assertThat(updated.getRoles()).containsExactly(UserRole.ADMIN);
    }
}
