package com.example.backend.loan.application.port.out;

import com.example.backend.loan.domain.model.Loan;

/** Tells the applicant about a decision (approval with its conditions, or rejection with its reason). */
public interface ApplicantNotifierPort {

    void notifyDecision(Loan reviewed);
}
