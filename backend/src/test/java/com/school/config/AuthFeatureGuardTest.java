package com.school.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthFeatureGuardTest {

    private static final String REAL_CLIENT_ID = "1234567890-abc.apps.googleusercontent.com";
    private static final String REAL_CLIENT_SECRET = "a-real-google-client-secret";

    @Test
    void passwordLoginWithTheProdProfileRefusesToStart() {
        assertThatThrownBy(() -> new AuthFeatureGuard(prodWith("prod"), true, true, REAL_CLIENT_ID, REAL_CLIENT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password-login");
    }

    @Test
    void oauthEnabledInProdWithoutRealKeysRefusesToStart() {
        assertThatThrownBy(() -> new AuthFeatureGuard(prodWith("prod"), false, true, "not-configured", "not-configured"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    @Test
    void oauthDisabledInProdStartsWithoutKeys() {
        assertThatCode(() -> new AuthFeatureGuard(prodWith("prod"), false, false, "not-configured", "not-configured"))
                .doesNotThrowAnyException();
    }

    @Test
    void aProperlyConfiguredProdProfileStarts() {
        assertThatCode(() -> new AuthFeatureGuard(prodWith("prod"), false, true, REAL_CLIENT_ID, REAL_CLIENT_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordLoginIsAllowedOutsideProd() {
        assertThatCode(() -> new AuthFeatureGuard(new MockEnvironment(), true, true, "", ""))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment prodWith(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
