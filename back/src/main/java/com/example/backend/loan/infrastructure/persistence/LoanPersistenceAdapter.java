package com.example.backend.loan.infrastructure.persistence;

import com.example.backend.loan.application.port.out.LoanRepositoryPort;
import com.example.backend.loan.domain.model.Loan;
import com.example.backend.loan.domain.model.LoanStatus;
import com.example.backend.loan.domain.model.LoanView;
import com.example.backend.loan.infrastructure.cache.LoanCaches;
import com.example.backend.loan.infrastructure.persistence.SpringDataLoanRepository.LoanRow;
import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * JPA implementation of the loan port. Caching lives here, in infrastructure: the status reads that applicants
 * repeat ("is my loan approved yet?") are served from EhCache, and every write evicts the affected entries.
 * The cache manager is transaction-aware, so evictions apply only after the transaction commits.
 */
@Component
class LoanPersistenceAdapter implements LoanRepositoryPort {

    private final SpringDataLoanRepository repository;

    LoanPersistenceAdapter(SpringDataLoanRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable(cacheNames = LoanCaches.LOAN_BY_ID, key = "#id", unless = "#result == null")
    public Optional<Loan> findById(UUID id) {
        return repository.findById(id).map(LoanJpaEntity::toDomain);
    }

    @Override
    public Optional<Loan> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(LoanJpaEntity::toDomain);
    }

    @Override
    @Cacheable(cacheNames = LoanCaches.LOANS_BY_APPLICANT, key = "#applicantId")
    public List<Loan> findByApplicant(UUID applicantId) {
        return repository.findByApplicantIdOrderByCreatedAtDesc(applicantId).stream()
                .map(LoanJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countByApplicantAndStatus(UUID applicantId, LoanStatus status) {
        return repository.countByApplicantIdAndStatus(applicantId, status);
    }

    @Override
    public PageResult<LoanView> search(LoanStatus status, PageQuery page) {
        PageRequest request = PageRequest.of(page.page(), page.size());
        Page<LoanRow> rows = status == null
                ? repository.findAllWithApplicant(request)
                : repository.findByStatusWithApplicant(status, request);
        return new PageResult<>(rows.map(this::toView).getContent(), rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = LoanCaches.LOAN_BY_ID, key = "#result.id()"),
            @CacheEvict(cacheNames = LoanCaches.LOANS_BY_APPLICANT, key = "#result.applicantId()")
    })
    public Loan save(Loan loan) {
        LoanJpaEntity entity = loan.id() == null
                ? new LoanJpaEntity()
                : repository.findById(loan.id()).orElseThrow(() ->
                        new ResourceNotFoundException("Loan with id %s was not found.".formatted(loan.id())));
        entity.apply(loan);
        // Flush now so version conflicts surface inside the use case and the returned loan has its new version.
        return repository.saveAndFlush(entity).toDomain();
    }

    private LoanView toView(LoanRow row) {
        return new LoanView(row.getLoan().toDomain(), row.getEmail(), applicantName(row));
    }

    private static String applicantName(LoanRow row) {
        if (row.getDisplayName() != null) {
            return row.getDisplayName();
        }
        String fullName = String.join(" ",
                row.getFirstName() == null ? "" : row.getFirstName(),
                row.getLastName() == null ? "" : row.getLastName()).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}
