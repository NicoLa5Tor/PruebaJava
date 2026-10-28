package com.example.backend.user.domain.model;

public enum UserRole {
    /** Requests loans and follows their status. */
    USER,
    /** Reviews loan requests (approve or reject). */
    ADMIN,
    /** Reviews loans and also manages users and their roles. */
    SUPER_ADMIN
}
