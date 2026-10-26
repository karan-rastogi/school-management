package com.school.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String timezone,
        @Valid @NotNull Cors cors
) {

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            boolean allowCredentials,
            @PositiveOrZero long maxAgeSeconds
    ) {
    }
}
