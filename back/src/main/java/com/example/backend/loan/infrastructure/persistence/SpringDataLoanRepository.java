package com.example.backend.loan.infrastructure.persistence;

import com.example.backend.loan.domain.model.LoanStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataLoanRepository extends JpaRepository<LoanJpaEntity, UUID> {

    /** SELECT ... FOR UPDATE: concurrent reviewers of the same loan are serialized by the database. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loan from Loan loan where loan.id = :id")
    Optional<LoanJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<LoanJpaEntity> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId);

    long countByApplicantIdAndStatus(UUID applicantId, LoanStatus status);

    @Query(value = """
            select loan as loan, user.email as email, user.displayName as displayName,
                   user.firstName as firstName, user.lastName as lastName
            from Loan loan join User user on user.id = loan.applicantId
            order by loan.createdAt desc
            """, countQuery = "select count(loan) from Loan loan")
    Page<LoanRow> findAllWithApplicant(Pageable pageable);

    @Query(value = """
            select loan as loan, user.email as email, user.displayName as displayName,
                   user.firstName as firstName, user.lastName as lastName
            from Loan loan join User user on user.id = loan.applicantId
            where loan.status = :status
            order by loan.createdAt desc
            """, countQuery = "select count(loan) from Loan loan where loan.status = :status")
    Page<LoanRow> findByStatusWithApplicant(@Param("status") LoanStatus status, Pageable pageable);

    interface LoanRow {
        LoanJpaEntity getLoan();

        String getEmail();

        String getDisplayName();

        String getFirstName();

        String getLastName();
    }
}
