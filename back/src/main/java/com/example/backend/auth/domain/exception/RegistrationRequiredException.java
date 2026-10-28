package com.example.backend.auth.domain.exception;

/**
 * The Logto identity is valid but has no local account yet. Carries the identity's profile so the client can
 * prefill the registration form; the email itself is always taken from the verified token when registering.
 */
public class RegistrationRequiredException extends RuntimeException {
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String displayName;

    public RegistrationRequiredException(String email, String firstName, String lastName, String displayName) {
        super("No account exists for this identity yet. Complete the registration to continue.");
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
    }

    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getDisplayName() { return displayName; }
}
