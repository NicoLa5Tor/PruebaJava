package com.example.backend.loan.application.port.out;

import com.example.backend.loan.domain.model.LoanReview;

public interface LoanReviewLogPort {

    void record(LoanReview review);
}
