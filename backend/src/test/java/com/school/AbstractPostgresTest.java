package com.school;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots a real PostgreSQL for the integration tests. Docker (via Testcontainers) is used when it is
 * available; otherwise an embedded PostgreSQL is started so the suite still runs on machines without
 * Docker. If neither can be started the tests are skipped with {@link EnabledIf} rather than failing.
 */
@SpringBootTest
@EnabledIf("dataSourceAvailable")
public abstract class AbstractPostgresTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final boolean DATA_SOURCE_READY;
    private static EmbeddedPostgres embedded;

    static {
        boolean ready = false;
        if (dockerAvailable()) {
            try {
                POSTGRES.start();
                ready = true;
            } catch (RuntimeException ignored) {
                // Docker looked available but failed to start; fall back to embedded PostgreSQL.
            }
        }
        if (!ready) {
            try {
                embedded = EmbeddedPostgres.builder().start();
                ready = true;
            } catch (Exception ignored) {
                embedded = null;
            }
        }
        DATA_SOURCE_READY = ready;
    }

    static boolean dataSourceAvailable() {
        return DATA_SOURCE_READY;
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable failure) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (embedded != null) {
            registry.add("spring.datasource.url", () -> embedded.getJdbcUrl("postgres", "postgres"));
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }
}
