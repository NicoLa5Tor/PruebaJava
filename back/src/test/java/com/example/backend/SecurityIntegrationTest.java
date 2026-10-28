package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.model.auth.RefreshToken;
import com.example.backend.model.user.User;
import com.example.backend.model.user.UserRole;
import com.example.backend.model.user.UserStatus;
import com.example.backend.repository.auth.RefreshTokenRepository;
import com.example.backend.repository.user.UserRepository;
import com.example.backend.service.auth.LocalJwtService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LocalJwtService jwtService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void anonymousRequestsGetUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotManageUsersOrEscalateRoles() throws Exception {
        User user = save("regular@example.com", Set.of(UserRole.USER));
        String bearer = "Bearer " + jwtService.create(user);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("regular@example.com"));
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRoles()).containsExactly(UserRole.USER);
    }

    @Test
    void adminCanListUsers() throws Exception {
        User admin = save("admin@example.com", Set.of(UserRole.ADMIN));
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.create(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void userCanEditOwnProfileButNotPrivilegedFields() throws Exception {
        User user = save("profile@example.com", Set.of(UserRole.USER));
        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.create(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo\",\"roles\":[\"ADMIN\"],\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        User admin = save("self@example.com", Set.of(UserRole.ADMIN));
        String bearer = "Bearer " + jwtService.create(admin);

        mockMvc.perform(delete("/api/v1/users/" + admin.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/users/" + admin.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/users/" + admin.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminCanSearchAndDeleteUserWithSessions() throws Exception {
        User admin = save("boss@example.com", Set.of(UserRole.ADMIN));
        User target = save("findme-target@example.com", Set.of(UserRole.USER));
        storeRefreshToken(target);
        String bearer = "Bearer " + jwtService.create(admin);

        mockMvc.perform(get("/api/v1/users").param("search", "FINDME").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("findme-target@example.com"));
        mockMvc.perform(delete("/api/v1/users/" + target.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    void refreshTokenTravelsOnlyInHttpOnlyCookieAndRotates() throws Exception {
        User user = save("cookie@example.com", Set.of(UserRole.USER));
        String firstRefresh = storeRefreshToken(user);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", firstRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveUserCannotRefresh() throws Exception {
        User user = save("inactive@example.com", Set.of(UserRole.USER));
        String refresh = storeRefreshToken(user);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh)))
                .andExpect(status().isUnauthorized());
    }

    private String storeRefreshToken(User user) throws Exception {
        String raw = UUID.randomUUID().toString();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        refreshTokenRepository.save(token);
        return raw;
    }

    private User save(String email, Set<UserRole> roles) {
        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(roles);
        return userRepository.save(user);
    }
}
