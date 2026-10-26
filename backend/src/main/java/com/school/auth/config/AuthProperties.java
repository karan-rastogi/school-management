package com.school.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull Otp otp
) {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {

        public Jwt {
            if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "app.auth.jwt.secret must be at least " + MINIMUM_HMAC_KEY_BYTES
                                + " bytes for HS256; set the JWT_SECRET environment variable");
            }
        }
    }

    public record Cookie(
            boolean secure,
            @NotBlank String sameSite
    ) {
    }

    public record Otp(
            @NotNull Duration ttl,
            @Min(4) @Max(10) int length,
            @Min(1) int maxAttempts,
            @NotBlank String sender,
            @Valid @NotNull RateLimit rateLimit
    ) {
    }

    public record RateLimit(
            @Min(1) int phoneRequests,
            @Min(1) int phoneVerifies,
            @Min(1) int ipRequests,
            @Min(1) int ipVerifies,
            @NotNull Duration window
    ) {
    }
}
