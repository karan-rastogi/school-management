package com.school.config;

import com.school.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application with the {@code prod} profile to prove that {@code application-prod.yml} is
 * valid and applies the production settings. The required secrets are supplied as test properties
 * (the prod profile deliberately has no defaults for them), and the datasource comes from
 * {@link AbstractPostgresTest}.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "PORT=9999",
        "app.auth.jwt.secret=test-secret-that-is-long-enough-for-hs256-0123456789",
        "app.notices.signing-secret=test-signing-secret-0123456789-abcdefghij",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.cors.allowed-origins=https://frontend.example.test",
        "app.auth.frontend-base-url=https://frontend.example.test"
})
class ProdProfileConfigurationTest extends AbstractPostgresTest {

    @Value("${server.forward-headers-strategy}")
    private String forwardHeadersStrategy;

    @Value("${server.port}")
    private int serverPort;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Value("${spring.jpa.show-sql}")
    private boolean showSql;

    @Value("${spring.flyway.enabled}")
    private boolean flywayEnabled;

    @Value("${logging.level.root}")
    private String rootLogLevel;

    @Test
    void theProductionProfileAppliesTheHardenedSettings() {
        assertThat(forwardHeadersStrategy).isEqualTo("framework");
        assertThat(serverPort).as("the app honours the PORT variable").isEqualTo(9999);
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(showSql).isFalse();
        assertThat(flywayEnabled).isTrue();
        assertThat(rootLogLevel).isEqualTo("WARN");
    }
}
