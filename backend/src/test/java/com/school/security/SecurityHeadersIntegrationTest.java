package com.school.security;

import com.school.AbstractPostgresTest;
import com.school.auth.security.JwtService;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityHeadersIntegrationTest extends AbstractPostgresTest {

    private static final String ACCESS_COOKIE = "access_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void hstsIsSentOnlyOnSecureRequests() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void anUnknownPathIsDeniedForAnonymousCallers() throws Exception {
        mockMvc.perform(get("/some/unlisted/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anUnknownPathIsDeniedEvenForAuthenticatedCallers() throws Exception {
        User admin = ensureUser("+919333000001", "headers-admin@school.test", "Headers Admin");
        String token = jwtService.issueAccessToken(admin.getId(), Role.ADMIN);

        mockMvc.perform(get("/some/unlisted/path").cookie(new Cookie(ACCESS_COOKIE, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private User ensureUser(String phone, String email, String name) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setName(name);
            user.setActive(true);
            User saved = userRepository.saveAndFlush(user);
            UserRole role = new UserRole();
            role.setUserId(saved.getId());
            role.setRole(Role.ADMIN);
            userRoleRepository.saveAndFlush(role);
            return saved;
        });
    }
}
