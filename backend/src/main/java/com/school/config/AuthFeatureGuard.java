package com.school.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the authentication feature flags are unsafe for the active profile.
 *
 * <ul>
 *   <li>Password login is a testing convenience and must never be enabled with the {@code prod}
 *       profile.</li>
 *   <li>When OAuth is enabled in {@code prod}, real Google client credentials are required; the
 *       placeholder default is rejected so a misconfigured deployment cannot boot.</li>
 * </ul>
 */
@Component
public class AuthFeatureGuard {

    private static final String PLACEHOLDER = "not-configured";

    public AuthFeatureGuard(
            Environment environment,
            @Value("${app.auth.password-login.enabled:false}") boolean passwordLoginEnabled,
            @Value("${app.auth.oauth.enabled:true}") boolean oauthEnabled,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String googleClientSecret
    ) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (passwordLoginEnabled) {
            throw new IllegalStateException(
                    "app.auth.password-login.enabled must not be true with the 'prod' profile: "
                            + "password login is for local testing only.");
        }
        if (oauthEnabled && (notConfigured(googleClientId) || notConfigured(googleClientSecret))) {
            throw new IllegalStateException(
                    "Google OAuth is enabled but GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET are not set. "
                            + "Set them, or disable OAuth with AUTH_OAUTH_ENABLED=false.");
        }
    }

    private static boolean notConfigured(String value) {
        return value == null || value.isBlank() || PLACEHOLDER.equals(value);
    }
}
