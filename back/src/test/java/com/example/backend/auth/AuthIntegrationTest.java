package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort.ExternalIdentity;
import com.example.backend.support.IntegrationTest;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void passwordLoginIssuesAnAccessTokenAndAnHttpOnlyRefreshCookie() throws Exception {
        User user = createUserWithPassword("login", "123", UserRole.USER);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"123\"}".formatted(user.getEmail().toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
    }

    @Test
    void wrongPasswordUnknownEmailAndInactiveUserGet401() throws Exception {
        User user = createUserWithPassword("wrong", "123", UserRole.USER);
        User inactive = createUserWithPassword("inactive", "123", UserRole.USER);
        inactive.setStatus(UserStatus.INACTIVE);
        users.save(inactive);

        login(user.getEmail(), "456").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        login("nobody@example.com", "123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        login(inactive.getEmail(), "123").andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequestIsValidated() throws Exception {
        login("not-an-email", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void refreshRotatesTheCookieAndTheOldOneStopsWorking() throws Exception {
        User user = createUserWithPassword("refresh", "123", UserRole.USER);
        String firstRefresh = refreshCookieOf(login(user.getEmail(), "123").andReturn());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertThat(refreshCookieOf(refreshed)).isNotEqualTo(firstRefresh);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", firstRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownLogtoIdentityMustRegisterAndGetsOnlyTheUserRole() throws Exception {
        when(identityVerifier.verify("new-token")).thenReturn(
                new ExternalIdentity("logto-new", "New.Person@Example.com", "Nueva", "Persona", "Nueva P", "https://img/x.png"));

        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.email").value("New.Person@Example.com"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(users.findByLogtoSubject("logto-new")).isEmpty();

        // Email, roles and status in the body are ignored: email comes from the token, the role is always USER.
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"new-token","firstName":"Ana","lastName":"Gómez","displayName":"Ana G",
                         "email":"attacker@example.com","roles":["SUPER_ADMIN"],"status":"ACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")));

        User created = users.findByLogtoSubject("logto-new").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("new.person@example.com");
        assertThat(created.getRoles()).containsExactly(UserRole.USER);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"new-token\",\"firstName\":\"Ana\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accountPreCreatedBySuperAdminIsLinkedOnFirstLogtoSignIn() throws Exception {
        User invited = createUser("invited", UserRole.ADMIN);
        when(identityVerifier.verify("invited-token")).thenReturn(
                new ExternalIdentity("logto-invited", invited.getEmail(), null, null, null, null));

        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"invited-token\"}"))
                .andExpect(status().isOk());

        assertThat(users.findById(invited.getId()).orElseThrow().getLogtoSubject()).isEqualTo("logto-invited");
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private static String refreshCookieOf(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }
}
