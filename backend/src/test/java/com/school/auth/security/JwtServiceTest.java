package com.school.auth.security;

import com.school.auth.config.AuthConfig;
import com.school.auth.config.AuthProperties;
import com.school.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T06:00:00Z");
    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-0123456789";

    private AuthConfig authConfig;
    private Clock clock;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void roundTripsTheUserIdAndActiveRoleWithoutAnyPersonalData() {
        JwtService jwtService = jwtService(Duration.ofMinutes(15));

        String token = jwtService.issueAccessToken(42L, Role.PARENT);
        Optional<JwtService.AccessTokenClaims> claims = jwtService.readAccessToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(42L);
        assertThat(claims.get().activeRole()).isEqualTo(Role.PARENT);
        assertThat(token).doesNotContain("42@", "@", "+91");
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        JwtService issuer = jwtService(Duration.ofMinutes(15));
        String token = issuer.issueAccessToken(42L, Role.ADMIN);

        AuthProperties otherProperties = properties(
                "another-secret-that-is-also-long-enough-for-hs256-9876543210", Duration.ofMinutes(15));
        JwtService verifier = new JwtService(
                authConfig.jwtEncoder(otherProperties), authConfig.jwtDecoder(otherProperties), otherProperties, clock);

        assertThat(verifier.readAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        AuthProperties properties = properties(SECRET, Duration.ofMinutes(15));
        JwtService issuer = new JwtService(
                authConfig.jwtEncoder(properties), authConfig.jwtDecoder(properties), properties, clock);

        String token = issuer.issueAccessToken(42L, Role.ADMIN);
        assertThat(issuer.readAccessToken(token)).isPresent();

        JwtService later = new JwtService(
                authConfig.jwtEncoder(properties), decoderValidatingAt(NOW.plus(Duration.ofHours(1))), properties, clock);

        assertThat(later.readAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        JwtService jwtService = jwtService(Duration.ofMinutes(15));

        assertThat(jwtService.readAccessToken("not-a-jwt")).isEmpty();
    }

    private JwtService jwtService(Duration accessTokenTtl) {
        AuthProperties properties = properties(SECRET, accessTokenTtl);
        JwtEncoder encoder = authConfig.jwtEncoder(properties);
        JwtDecoder decoder = authConfig.jwtDecoder(properties);
        return new JwtService(encoder, decoder, properties, clock);
    }

    private JwtDecoder decoderValidatingAt(Instant validationTime) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(Clock.fixed(validationTime, ZoneOffset.UTC));
        decoder.setJwtValidator(timestampValidator);
        return decoder;
    }

    private AuthProperties properties(String secret, Duration accessTokenTtl) {
        return new AuthProperties(
                "http://localhost:5173",
                new AuthProperties.Jwt(secret, "school-management", accessTokenTtl, Duration.ofDays(30)),
                new AuthProperties.Cookie(true, "Lax"),
                new AuthProperties.Otp(
                        Duration.ofMinutes(5),
                        6,
                        5,
                        "console",
                        new AuthProperties.RateLimit(3, 10, 10, 30, Duration.ofMinutes(15))));
    }
}
