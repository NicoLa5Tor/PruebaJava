package com.example.backend.user.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** A person who can sign in. Persistence-agnostic: the JPA adapter maps it to and from its own entity. */
public class User {

    private final UUID id;
    private final Instant createdAt;
    private final Instant updatedAt;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private String logtoSubject;
    private boolean emailVerified;
    private String passwordHash;
    private UserStatus status = UserStatus.ACTIVE;
    private Set<UserRole> roles = EnumSet.of(UserRole.USER);

    /** Rehydrates a stored user. */
    public User(UUID id, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new, not yet stored user: active and with the USER role. */
    public static User newUser(String email) {
        User user = new User(null, null, null);
        user.setEmail(email);
        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public void addRole(UserRole role) {
        roles.add(role);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getLogtoSubject() {
        return logtoSubject;
    }

    public void setLogtoSubject(String logtoSubject) {
        this.logtoSubject = logtoSubject;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Set<UserRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    /** An empty role set falls back to USER so every account keeps at least one role. */
    public void setRoles(Set<UserRole> roles) {
        this.roles = roles == null || roles.isEmpty() ? EnumSet.of(UserRole.USER) : EnumSet.copyOf(roles);
    }
}
