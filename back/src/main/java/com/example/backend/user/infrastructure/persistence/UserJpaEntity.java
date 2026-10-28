package com.example.backend.user.infrastructure.persistence;

import com.example.backend.shared.infrastructure.persistence.BaseJpaEntity;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;

@Entity(name = "User")
@Table(name = "app_users")
public class UserJpaEntity extends BaseJpaEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 150)
    private String displayName;

    @Column(length = 2048)
    private String avatarUrl;

    @Column(unique = true, length = 255)
    private String logtoSubject;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<UserRole> roles = EnumSet.of(UserRole.USER);

    /** Copies every mutable field from the domain object; id and audit columns stay under JPA control. */
    void apply(User user) {
        email = user.getEmail();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        displayName = user.getDisplayName();
        avatarUrl = user.getAvatarUrl();
        logtoSubject = user.getLogtoSubject();
        emailVerified = user.isEmailVerified();
        passwordHash = user.getPasswordHash();
        status = user.getStatus();
        roles.clear();
        roles.addAll(user.getRoles());
    }

    User toDomain() {
        User user = new User(getId(), getCreatedAt(), getUpdatedAt());
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        user.setLogtoSubject(logtoSubject);
        user.setEmailVerified(emailVerified);
        user.setPasswordHash(passwordHash);
        user.setStatus(status);
        user.setRoles(roles);
        return user;
    }
}
