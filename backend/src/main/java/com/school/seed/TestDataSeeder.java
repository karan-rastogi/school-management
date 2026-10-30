package com.school.seed;

import com.school.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds a test data set on startup, but only when password login is enabled (the flag that gates
 * the whole feature) and the database is still empty. Every password comes from the
 * {@code SEED_TEST_PASSWORD} environment variable — there is no hard-coded fallback — and the log
 * records counts only, never a password or hash.
 */
@Component
@ConditionalOnProperty(name = "app.auth.password-login.enabled", havingValue = "true")
public class TestDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataSeeder.class);

    private final UserRepository userRepository;
    private final SeedService seedService;
    private final String seedPassword;

    public TestDataSeeder(
            UserRepository userRepository,
            SeedService seedService,
            @Value("${SEED_TEST_PASSWORD:}") String seedPassword
    ) {
        this.userRepository = userRepository;
        this.seedService = seedService;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Test data seeding skipped: the database already holds data");
            return;
        }
        if (seedPassword == null || seedPassword.isBlank()) {
            log.warn("Test data seeding skipped: SEED_TEST_PASSWORD is not set");
            return;
        }

        SeedCounts counts = seedService.seedAll(seedPassword);
        log.info("Seeded test data: {}", counts);
    }
}
