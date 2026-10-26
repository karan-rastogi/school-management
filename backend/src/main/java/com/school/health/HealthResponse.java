package com.school.health;

import java.time.ZonedDateTime;

public record HealthResponse(
        String status,
        String application,
        String timeZone,
        ZonedDateTime timestamp
) {
}
