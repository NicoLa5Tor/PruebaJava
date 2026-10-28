package com.example.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.support.IntegrationTest;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class UserManagementIntegrationTest extends IntegrationTest {

    @Test
    void anonymousRequestsGetUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlySuperAdminsManageUsers() throws Exception {
        User user = createUser("regular", UserRole.USER);
        User admin = createUser("reviewer", UserRole.ADMIN);
        User superAdmin = createUser("boss", UserRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(superAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserCannotEscalateRoles() throws Exception {
        User user = createUser("escalate", UserRole.USER);

        mockMvc.perform(put("/api/v1/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden());

        assertThat(users.findById(user.getId()).orElseThrow().getRoles()).containsExactly(UserRole.USER);
    }

    @Test
    void userCanEditOwnProfileButNotPrivilegedFields() throws Exception {
        User user = createUser("profile", UserRole.USER);

        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo\",\"roles\":[\"ADMIN\"],\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void superAdminCreatesAnAdminThatCanThenSignInWithPassword() throws Exception {
        User superAdmin = createUser("creator", UserRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"new.reviewer@example.com","displayName":"Revisor",
                                 "roles":["ADMIN"],"password":"reviewer-pass"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.reviewer@example.com\",\"password\":\"reviewer-pass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCannotLockThemselvesOut() throws Exception {
        User superAdmin = createUser("self", UserRole.SUPER_ADMIN);
        String bearer = bearer(superAdmin);

        mockMvc.perform(delete("/api/v1/users/" + superAdmin.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/users/" + superAdmin.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/users/" + superAdmin.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void superAdminCanSearchAndDeleteUsers() throws Exception {
        User superAdmin = createUser("searcher", UserRole.SUPER_ADMIN);
        User target = createUser("findme-target", UserRole.USER);
        String bearer = bearer(superAdmin);

        mockMvc.perform(get("/api/v1/users").param("search", "FINDME-TARGET").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(target.getEmail()));
        mockMvc.perform(delete("/api/v1/users/" + target.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        assertThat(users.findById(target.getId())).isEmpty();
    }

    @Test
    void malformedRequestsGetProblemDetails() throws Exception {
        User superAdmin = createUser("malformed", UserRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/users/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(superAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
        mockMvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content("{broken json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
