package com.school.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String application;
    private final Clock clock;

    public HealthController(@Value("${spring.application.name}") String application, Clock clock) {
        this.application = application;
        this.clock = clock;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                application,
                clock.getZone().getId(),
                ZonedDateTime.now(clock)
        );
    }
}
