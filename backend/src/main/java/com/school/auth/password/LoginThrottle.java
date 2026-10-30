package com.school.auth.password;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed email + password sign-in attempts and locks a subject for a fixed period once the
 * ceiling is reached. The subject is the normalised email address, so an unknown address is locked
 * exactly like a known one and the endpoint cannot be used to probe which accounts exist.
 *
 * <p>This is in-memory and per-instance, matching {@link com.school.common.ratelimit.RateLimiter};
 * it is a guard rail, not a distributed lock.
 */
@Component
public class LoginThrottle {

    private static final int MAX_TRACKED_SUBJECTS = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration lockout;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginThrottle(
            Clock clock,
            @Value("${app.auth.password-login.max-failed-attempts:5}") int maxFailures,
            @Value("${app.auth.password-login.lockout-duration:PT15M}") Duration lockout
    ) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockout = lockout;
    }

    public void assertNotLocked(String subject) {
        Attempt attempt = attempts.get(subject);
        if (attempt == null || attempt.lockedUntil() == null) {
            return;
        }
        if (attempt.lockedUntil().isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "Too many failed sign-in attempts. Please try again later.");
        }
        attempts.remove(subject);
    }

    public void recordFailure(String subject) {
        Instant now = Instant.now(clock);
        evictIfCrowded();
        attempts.compute(subject, (ignored, current) -> {
            int count = current == null || current.lockedUntil() != null ? 1 : current.count() + 1;
            Instant lockedUntil = count >= maxFailures ? now.plus(lockout) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    public void recordSuccess(String subject) {
        attempts.remove(subject);
    }

    private void evictIfCrowded() {
        if (attempts.size() > MAX_TRACKED_SUBJECTS) {
            Instant now = Instant.now(clock);
            attempts.entrySet().removeIf(entry ->
                    entry.getValue().lockedUntil() == null || !entry.getValue().lockedUntil().isAfter(now));
        }
    }

    private record Attempt(int count, Instant lockedUntil) {
    }
}
