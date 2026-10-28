package com.example.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.loan.application.port.in.LoanQueryUseCase;
import com.example.backend.loan.application.port.in.RequestLoanUseCase;
import com.example.backend.loan.application.port.in.ReviewLoanUseCase;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.infrastructure.cache.LoanCaches;
import com.example.backend.support.IntegrationTest;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the status reads are served from EhCache: after the first read the row is changed behind the
 * application's back, and the cached value keeps being returned until a write through the use cases evicts it.
 */
class LoanCacheIntegrationTest extends IntegrationTest {

    @Autowired private RequestLoanUseCase requests;
    @Autowired private ReviewLoanUseCase reviews;
    @Autowired private LoanQueryUseCase queries;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void cacheManagerIsEhCacheThroughJCache() {
        assertThat(cacheManager.getClass().getName()).contains("JCache");
        assertThat(cacheManager.getCacheNames()).contains(LoanCaches.LOAN_BY_ID, LoanCaches.LOANS_BY_APPLICANT);
    }

    @Test
    void repeatedStatusReadsAreServedFromTheCacheUntilAReviewEvictsThem() {
        User applicant = createUser("cached", UserRole.USER);
        User admin = createUser("cache-admin", UserRole.ADMIN);
        Loan loan = requests.request(applicant.getId(), new BigDecimal("1000"), 12);

        assertThat(queries.findById(loan.id(), applicant.getId(), false).status()).isEqualTo(LoanStatus.PENDING);
        assertThat(queries.findMine(applicant.getId())).hasSize(1);

        jdbc.update("update loans set term_months = 99 where id = ?", loan.id());
        assertThat(queries.findById(loan.id(), applicant.getId(), false).termMonths())
                .as("second read comes from the cache, not the database").isEqualTo(12);
        assertThat(queries.findMine(applicant.getId()).getFirst().termMonths()).isEqualTo(12);

        reviews.approve(loan.id(), admin.getId());

        Loan reloaded = queries.findById(loan.id(), applicant.getId(), false);
        assertThat(reloaded.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(reloaded.termMonths()).as("the approval evicted the entry").isEqualTo(99);
        assertThat(queries.findMine(applicant.getId()).getFirst().status()).isEqualTo(LoanStatus.APPROVED);
    }
}
