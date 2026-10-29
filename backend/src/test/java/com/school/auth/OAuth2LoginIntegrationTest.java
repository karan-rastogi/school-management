package com.school.auth;

import com.school.AbstractPostgresTest;
import com.school.auth.security.OAuth2LoginSuccessHandler;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Google sign-in rules against the real database and session machinery:
 * there is no auto-signup (an unregistered address is rejected), a deactivated account is refused,
 * and a pre-registered active account gets a real session cookie.
 */
class OAuth2LoginIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private OAuth2LoginSuccessHandler handler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void anUnregisteredGoogleEmailIsRejectedAndNoAccountIsCreated() throws Exception {
        String email = "unregistered-oauth@school.test";
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();

        MockHttpServletResponse response = signIn(email);

        assertThat(response.getRedirectedUrl()).endsWith("/login?error=not_registered");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(userRepository.findByEmailIgnoreCase(email))
                .as("sign-in must never auto-create an account")
                .isEmpty();
    }

    @Test
    void aDeactivatedAccountIsRejected() throws Exception {
        String email = "disabled-oauth@school.test";
        ensureUser("+919000000302", email, "Disabled OAuth", false, Role.PARENT);

        MockHttpServletResponse response = signIn(email);

        assertThat(response.getRedirectedUrl()).endsWith("/login?error=account_disabled");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void aPreRegisteredActiveAccountStartsASession() throws Exception {
        String email = "active-oauth@school.test";
        ensureUser("+919000000303", email, "Active OAuth", true, Role.ADMIN);

        MockHttpServletResponse response = signIn(email);

        assertThat(response.getRedirectedUrl()).endsWith("/auth/callback");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("access_token=").contains("HttpOnly"));
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("refresh_token="));
    }

    private MockHttpServletResponse signIn(String email) throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser, List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response;
    }

    private User ensureUser(String phone, String email, String name, boolean active, Role... roles) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setPhone(phone);
            user.setEmail(email);
            user.setName(name);
            user.setActive(active);
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
}
