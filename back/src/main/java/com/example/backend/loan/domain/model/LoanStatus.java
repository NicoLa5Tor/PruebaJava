package com.example.backend.loan.domain.model;

public enum LoanStatus {
    /** Waiting for an admin; the applicant can still modify or cancel it. */
    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the applicant before review. */
    CANCELLED
}
