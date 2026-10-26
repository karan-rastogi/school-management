package com.school.auth.security;

import com.school.auth.config.AuthProperties;
import com.school.auth.service.AuthService;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuthProperties authProperties;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            AuthService authService,
            AuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String email = extractEmail(authentication);
        if (email == null) {
            log.warn("Rejected external sign-in: the provider did not return a verified email address");
            redirect(response, loginErrorUrl("no_email"));
            return;
        }

        Optional<User> account = userRepository.findByEmailIgnoreCase(email);
        if (account.isEmpty()) {
            log.warn("Rejected external sign-in: the address is not pre-registered");
            redirect(response, loginErrorUrl("not_registered"));
            return;
        }

        User user = account.get();
        if (!user.isActive()) {
            log.warn("Rejected external sign-in for user id {}: the account is inactive", user.getId());
            redirect(response, loginErrorUrl("account_disabled"));
            return;
        }

        authService.startSession(user, response);
        log.info("Signed in user id {} using an external identity provider", user.getId());
        redirect(response, authProperties.frontendBaseUrl() + "/auth/callback");
    }

    private String extractEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser && isUsable(oidcUser.getEmail())) {
            return oidcUser.getEmail();
        }
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttributes().get("email");
            if (email instanceof String value && isUsable(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isUsable(String email) {
        return email != null && !email.isBlank();
    }

    private String loginErrorUrl(String code) {
        return authProperties.frontendBaseUrl() + "/login?error=" + code;
    }

    private void redirect(HttpServletResponse response, String location) throws IOException {
        response.sendRedirect(location);
    }
}
