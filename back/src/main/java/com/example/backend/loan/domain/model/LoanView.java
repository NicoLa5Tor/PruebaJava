package com.example.backend.loan.domain.model;

/** A loan with the applicant's contact data, for the admins' review queue. */
public record LoanView(Loan loan, String applicantEmail, String applicantName) {
}
