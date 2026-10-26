package com.school.auth.security;

import com.school.auth.config.AuthProperties;
import com.school.auth.service.AuthService;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private OidcUser oidcUser;

    private OAuth2LoginSuccessHandler handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(userRepository, authService, authProperties());
        response = new MockHttpServletResponse();
    }

    @Test
    void rejectsAnAddressThatWasNeverPreRegisteredAndNeverCreatesAnAccount() throws Exception {
        when(oidcUser.getEmail()).thenReturn("stranger@example.com");
        when(userRepository.findByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=not_registered");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        verify(authService, never()).startSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsADeactivatedAccount() throws Exception {
        when(oidcUser.getEmail()).thenReturn("parent@example.com");
        when(userRepository.findByEmailIgnoreCase("parent@example.com")).thenReturn(Optional.of(user(false)));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=account_disabled");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        verify(authService, never()).startSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startsASessionForAPreRegisteredActiveAccount() throws Exception {
        User user = user(true);
        when(oidcUser.getEmail()).thenReturn("parent@example.com");
        when(userRepository.findByEmailIgnoreCase("parent@example.com")).thenReturn(Optional.of(user));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/auth/callback");
        verify(authService).startSession(user, response);
    }

    @Test
    void rejectsASignInThatCarriesNoEmail() throws Exception {
        when(oidcUser.getEmail()).thenReturn(null);

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=no_email");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void neverPutsTheAddressInTheRedirectUrl() throws Exception {
        when(oidcUser.getEmail()).thenReturn("stranger@example.com");
        when(userRepository.findByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication());

        assertThat(response.getRedirectedUrl()).doesNotContain("stranger");
        assertThat(response.getRedirectedUrl()).doesNotContain("@");
    }

    private OAuth2AuthenticationToken authentication() {
        return new OAuth2AuthenticationToken(oidcUser, List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");
    }

    private User user(boolean active) {
        User user = new User();
        user.setId(7L);
        user.setName("Ravi Parent");
        user.setEmail("parent@example.com");
        user.setActive(active);
        return user;
    }

    private AuthProperties authProperties() {
        return new AuthProperties(
                FRONTEND,
                new AuthProperties.Jwt(
                        "test-secret-that-is-long-enough-for-hs256-0123456789",
                        "school-management",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30)),
                new AuthProperties.Cookie(true, "Lax"),
                new AuthProperties.Otp(
                        Duration.ofMinutes(5),
                        6,
                        5,
                        "console",
                        new AuthProperties.RateLimit(3, 10, 10, 30, Duration.ofMinutes(15))));
    }
}
