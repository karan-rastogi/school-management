package com.school.common.ratelimit;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple fixed-window, in-memory rate limiter keyed by an arbitrary string (phone, client
 * address, endpoint, ...). Each {@link #check} call counts one attempt for the key and throws
 * {@link ApiException} with {@code 429 RATE_LIMITED} once the limit within the window is exceeded.
 */
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Duration EVICTION_WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key, int limit, Duration window) {
        Instant now = Instant.now(clock);

        Counter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.isStale(now, window)) {
                return new Counter(now, 1);
            }
            return new Counter(current.windowStart(), current.count() + 1);
        });

        evictIfCrowded(now);

        if (counter.count() > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "Too many requests. Please wait a few minutes and try again.");
        }
    }

    private void evictIfCrowded(Instant now) {
        if (counters.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().isStale(now, EVICTION_WINDOW));
    }

    private record Counter(Instant windowStart, int count) {

        boolean isStale(Instant now, Duration window) {
            return !windowStart.plus(window).isAfter(now);
        }
    }
}
