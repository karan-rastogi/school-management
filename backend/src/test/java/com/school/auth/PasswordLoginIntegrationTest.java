package com.school.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.AbstractPostgresTest;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the email + password sign-in against a real database and the real session
 * machinery: cookie issuance, the generic 401, the five-attempt lockout, logout, change-password
 * and the admin password reset. Passwords must never reach the log.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.password-login.enabled=true",
        "app.auth.password-login.rate-limit.ip-attempts=10000",
        "app.auth.password-login.rate-limit.email-attempts=10000",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Lax"
})
class PasswordLoginIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void passwordLoginIssuesTheSameSessionCookiesAndMeWorks() throws Exception {
        String email = "pw-admin@school.test";
        User user = ensureUserWithPassword(email, "correct-horse-battery", Role.ADMIN);

        MvcResult result = mockMvc.perform(login(email, "correct-horse-battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith(ACCESS_COOKIE + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith(REFRESH_COOKIE + "=")
                .contains("HttpOnly")
                .contains("Path=/auth"));

        String accessToken = cookieValue(result, ACCESS_COOKIE);
        mockMvc.perform(get("/me").cookie(new Cookie(ACCESS_COOKIE, accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        assertThat(user.getId()).isNotNull();
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameGeneric401() throws Exception {
        String email = "pw-generic@school.test";
        ensureUserWithPassword(email, "the-right-one", Role.TEACHER);

        MvcResult wrongPassword = mockMvc.perform(login(email, "the-wrong-one"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownEmail = mockMvc.perform(login("nobody-here@school.test", "the-wrong-one"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode first = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(unknownEmail.getResponse().getContentAsString());

        assertThat(first.get("status").asInt()).isEqualTo(401);
        assertThat(first.get("message").asText()).isEqualTo("Invalid email or password");
        assertThat(second.get("message").asText()).isEqualTo(first.get("message").asText());
        assertThat(second.get("code").asText()).isEqualTo(first.get("code").asText());
    }

    @Test
    void fiveFailedAttemptsLockTheAccountEvenWithTheRightPassword() throws Exception {
        String email = "pw-lockout@school.test";
        ensureUserWithPassword(email, "the-right-one", Role.TEACHER);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(login(email, "the-wrong-one")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(email, "the-right-one"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void logoutClearsTheCookiesAndEndsTheRefreshSession() throws Exception {
        String email = "pw-logout@school.test";
        ensureUserWithPassword(email, "the-right-one", Role.ADMIN);

        MvcResult login = mockMvc.perform(login(email, "the-right-one"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = cookieValue(login, REFRESH_COOKIE);

        MvcResult logout = mockMvc.perform(post("/auth/logout").cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith(ACCESS_COOKIE + "=").contains("Max-Age=0"));
        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith(REFRESH_COOKIE + "=").contains("Max-Age=0"));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresTheCurrentPasswordAndClearsTheFlag() throws Exception {
        String email = "pw-change@school.test";
        User user = ensureUserWithPassword(email, "temporary-pass", Role.PARENT);
        forceChangeOnNextLogin(user.getId());

        MvcResult login = mockMvc.perform(login(email, "temporary-pass"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        String accessToken = cookieValue(login, ACCESS_COOKIE);

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong-current", "newPassword", "brand-new-pass"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "temporary-pass", "newPassword", "brand-new-pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mockMvc.perform(login(email, "brand-new-pass")).andExpect(status().isOk());
        mockMvc.perform(login(email, "temporary-pass")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCanSetAPasswordThatForcesAChange() throws Exception {
        ensureUserWithPassword("pw-root-admin@school.test", "admin-password", Role.ADMIN);
        User target = ensureUserWithPassword("pw-target@school.test", "old-password", Role.TEACHER);

        MvcResult adminLogin = mockMvc.perform(login("pw-root-admin@school.test", "admin-password"))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = cookieValue(adminLogin, ACCESS_COOKIE);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/password")
                        .cookie(new Cookie(ACCESS_COOKIE, adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "short"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/users/" + target.getId() + "/password")
                        .cookie(new Cookie(ACCESS_COOKIE, adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "reset-password-1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(login("pw-target@school.test", "reset-password-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void passwordsAreNeverWrittenToTheLog() throws Exception {
        String email = "pw-logging@school.test";
        String password = "value-that-must-never-be-logged";
        ensureUserWithPassword(email, password, Role.TEACHER);

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            mockMvc.perform(login(email, password)).andExpect(status().isOk());
            mockMvc.perform(login(email, password + "-wrong")).andExpect(status().isUnauthorized());
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list)
                .as("no log event may contain the plaintext password")
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }

    private User ensureUserWithPassword(String email, String password, Role... roles) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setName("Test " + email);
            user.setActive(true);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setMustChangePassword(false);
            User saved = userRepository.saveAndFlush(user);
            for (Role role : roles) {
                UserRole userRole = new UserRole();
                userRole.setUserId(saved.getId());
                userRole.setRole(role);
                userRoleRepository.saveAndFlush(userRole);
            }
            return saved;
        });
    }

    private void forceChangeOnNextLogin(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);
    }

    private static String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in the response"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
