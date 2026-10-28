package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.model.user.User;
import com.example.backend.model.user.UserRole;
import com.example.backend.repository.user.UserRepository;
import com.example.backend.service.auth.LogtoIdTokenService;
import com.example.backend.service.auth.LogtoIdTokenService.LogtoIdentity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "auth.jwt.secret=test-only-secret-with-at-least-thirty-two-characters",
        "auth.logto.issuer=https://logto.arisma.com.co/oidc",
        "auth.logto.app-id=wpqq5ute5og9jfags2mom",
        "auth.admin-email="
})
class AuthRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @MockitoBean private LogtoIdTokenService logtoIdTokenService;

    @Test
    void unknownIdentityMustRegisterAndGetsOnlyTheUserRole() throws Exception {
        when(logtoIdTokenService.verify("new-token")).thenReturn(
                new LogtoIdentity("logto-new", "New.Person@Example.com", "Nueva", "Persona", "Nueva P", "https://img/x.png"));

        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.email").value("New.Person@Example.com"))
                .andExpect(jsonPath("$.firstName").value("Nueva"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(userRepository.findByLogtoSubject("logto-new")).isEmpty();

        // Email, roles and status in the body are ignored: email comes from the token, the role is always USER.
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"new-token","firstName":"Ana","lastName":"Gómez","displayName":"Ana G",
                         "email":"attacker@example.com","roles":["ADMIN"],"status":"ACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")));

        User created = userRepository.findByLogtoSubject("logto-new").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("new.person@example.com");
        assertThat(created.getFirstName()).isEqualTo("Ana");
        assertThat(created.getRoles()).containsExactly(UserRole.USER);
        assertThat(created.isEmailVerified()).isTrue();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"new-token\",\"firstName\":\"Ana\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"new-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accountPreCreatedByAdminSignsInDirectlyAndGetsLinked() throws Exception {
        User invited = new User();
        invited.setEmail("invited@example.com");
        userRepository.save(invited);
        when(logtoIdTokenService.verify("invited-token")).thenReturn(
                new LogtoIdentity("logto-invited", "invited@example.com", null, null, null, null));

        mockMvc.perform(post("/api/v1/auth/logto").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"invited-token\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmailIgnoreCase("invited@example.com").orElseThrow().getLogtoSubject())
                .isEqualTo("logto-invited");
    }

    @Test
    void registrationRequiresAFirstName() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"any\",\"firstName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
