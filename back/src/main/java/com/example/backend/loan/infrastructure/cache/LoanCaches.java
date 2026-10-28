package com.example.backend.loan.infrastructure.cache;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** EhCache 3 through JCache; sizes and TTLs are in {@code src/main/resources/ehcache.xml}. */
@Configuration
@EnableCaching
public class LoanCaches {
    public static final String LOAN_BY_ID = "loanById";
    public static final String LOANS_BY_APPLICANT = "loansByApplicant";

    /**
     * Defers puts and evictions until the surrounding transaction commits, so a reader can never re-cache a value
     * that is about to be rolled back or overwritten.
     */
    @Bean
    CacheManagerCustomizer<JCacheCacheManager> transactionAwareCaches() {
        return cacheManager -> cacheManager.setTransactionAware(true);
    }
}
