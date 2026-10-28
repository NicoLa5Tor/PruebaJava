package com.example.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.support.IntegrationTest;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class LoanIntegrationTest extends IntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void userRequestsAdminApprovesAndTheUserSeesTheNewStatus() throws Exception {
        User applicant = createUser("applicant", UserRole.USER);
        User admin = createUser("approver", UserRole.ADMIN);

        String loanId = requestLoan(applicant, "1000", 12);
        mockMvc.perform(get("/api/v1/loans/me").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v1/loans").param("status", "PENDING").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicantEmail".formatted(loanId))
                        .value(applicant.getEmail()));

        mockMvc.perform(approveRequest(loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(admin.getId().toString()));

        // The cached "my loans" list was evicted by the approval, so the user sees the change at once.
        mockMvc.perform(get("/api/v1/loans/me").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/loans/{id}", loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Integer audits = jdbc.queryForObject(
                "select count(*) from loan_reviews where loan_id = ?::uuid and decision = 'APPROVED'", Integer.class, loanId);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void approvalSetsTheOfferAndNotifiesTheApplicant() throws Exception {
        User applicant = createUser("notified", UserRole.USER);
        User admin = createUser("rate-setter", UserRole.ADMIN);
        String loanId = requestLoan(applicant, "1000000", 12);

        mockMvc.perform(patch("/api/v1/loans/{id}/approve", loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/loans/{id}/approve", loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annualRate\":150}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/loans/{id}/approve", loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annualRate\":26.82,\"message\":\"Bienvenido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualRate").value(26.82))
                .andExpect(jsonPath("$.monthlyPayment").value(org.hamcrest.Matchers.closeTo(94_559.6, 5.0)))
                .andExpect(jsonPath("$.reviewComment").value("Bienvenido"));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.unread").value(1));
        String inbox = mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LOAN_APPROVED"))
                .andExpect(jsonPath("$[0].loanId").value(loanId))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("26,82 % EA")))
                .andExpect(jsonPath("$[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        String notificationId = JsonPath.read(inbox, "$[0].id");

        // Nobody else can read or mark someone's notifications.
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void rejectionKeepsTheReviewerComment() throws Exception {
        User applicant = createUser("rejected", UserRole.USER);
        User superAdmin = createUser("super-reviewer", UserRole.SUPER_ADMIN);
        String loanId = requestLoan(applicant, "50000", 48);

        mockMvc.perform(patch("/api/v1/loans/{id}/reject", loanId).header(HttpHeaders.AUTHORIZATION, bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Capacidad de pago insuficiente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewComment").value("Capacidad de pago insuficiente"));
    }

    @Test
    void onlyAdminsCanReviewAndOnlyUsersCanRequest() throws Exception {
        User applicant = createUser("not-a-reviewer", UserRole.USER);
        User admin = createUser("not-an-applicant", UserRole.ADMIN);
        String loanId = requestLoan(applicant, "2000", 6);

        mockMvc.perform(approveRequest(loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"termMonths\":12}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(approveRequest(loanId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secondReviewOfTheSameLoanIsAConflict() throws Exception {
        User applicant = createUser("twice", UserRole.USER);
        User admin = createUser("first-reviewer", UserRole.ADMIN);
        String loanId = requestLoan(applicant, "3000", 12);

        mockMvc.perform(approveRequest(loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/loans/{id}/reject", loanId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("APPROVED")));
    }

    @Test
    void concurrentReviewsProduceExactlyOneDecision() throws Exception {
        User applicant = createUser("race", UserRole.USER);
        User firstAdmin = createUser("race-admin-1", UserRole.ADMIN);
        User secondAdmin = createUser("race-admin-2", UserRole.ADMIN);
        String loanId = requestLoan(applicant, "4000", 12);

        Callable<Integer> approve = () -> mockMvc.perform(approveRequest(loanId)
                .header(HttpHeaders.AUTHORIZATION, bearer(firstAdmin))).andReturn().getResponse().getStatus();
        Callable<Integer> reject = () -> mockMvc.perform(patch("/api/v1/loans/{id}/reject", loanId)
                .header(HttpHeaders.AUTHORIZATION, bearer(secondAdmin))).andReturn().getResponse().getStatus();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(approve);
            Future<Integer> second = executor.submit(reject);
            assertThat(new int[]{first.get(), second.get()}).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdown();
        }

        Integer audits = jdbc.queryForObject("select count(*) from loan_reviews where loan_id = ?::uuid",
                Integer.class, loanId);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void applicantCanModifyAndCancelOnlyTheirOwnPendingLoans() throws Exception {
        User applicant = createUser("owner", UserRole.USER);
        User stranger = createUser("stranger", UserRole.USER);
        String loanId = requestLoan(applicant, "1500", 12);

        mockMvc.perform(get("/api/v1/loans/{id}", loanId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/loans/{id}/cancel", loanId).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/loans/{id}", loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2500,\"termMonths\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(2500))
                .andExpect(jsonPath("$.termMonths").value(24));
        mockMvc.perform(patch("/api/v1/loans/{id}/cancel", loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(patch("/api/v1/loans/{id}/cancel", loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isConflict());
        // Loans are cancelled, never deleted.
        mockMvc.perform(delete("/api/v1/loans/{id}", loanId).header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void invalidRequestsReturnFieldErrors() throws Exception {
        User applicant = createUser("invalid", UserRole.USER);

        mockMvc.perform(post("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50,\"termMonths\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(
                        org.hamcrest.Matchers.containsInAnyOrder("amount", "termMonths")));
        mockMvc.perform(get("/api/v1/loans").param("status", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser("bad-filter", UserRole.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFourthPendingRequestIsRefused() throws Exception {
        User applicant = createUser("busy", UserRole.USER);
        for (int i = 0; i < 3; i++) {
            requestLoan(applicant, "1000", 12);
        }

        mockMvc.perform(post("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000,\"termMonths\":12}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void userWithLoansCannotBeDeletedButCanBeDeactivated() throws Exception {
        User applicant = createUser("has-loans", UserRole.USER);
        User superAdmin = createUser("cleaner", UserRole.SUPER_ADMIN);
        requestLoan(applicant, "1000", 12);

        mockMvc.perform(delete("/api/v1/users/{id}", applicant.getId()).header(HttpHeaders.AUTHORIZATION, bearer(superAdmin)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/users/{id}", applicant.getId()).header(HttpHeaders.AUTHORIZATION, bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder approveRequest(String loanId) {
        return patch("/api/v1/loans/{id}/approve", loanId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"annualRate\":24,\"message\":\"Bienvenido\"}");
    }

    private String requestLoan(User applicant, String amount, int termMonths) throws Exception {
        String body = mockMvc.perform(post("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":%s,\"termMonths\":%d}".formatted(amount, termMonths)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
