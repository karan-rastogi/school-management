package com.school.auth.security;

import com.school.auth.config.AuthConfig;
import com.school.auth.config.AuthProperties;
import com.school.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
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
    private static final String ISSUER = "school-management";

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
        String token = jwtService(Duration.ofMinutes(15)).issueAccessToken(42L, Role.ADMIN);

        String otherSecret = "another-secret-that-is-also-long-enough-for-hs256-9876543210";
        AuthProperties otherProperties = properties(otherSecret, Duration.ofMinutes(15));
        JwtService verifier = new JwtService(
                authConfig.jwtEncoder(otherProperties), decoderValidatingAt(otherSecret, NOW), otherProperties, clock);

        assertThat(verifier.readAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsATokenOnceItsLifetimeHasPassed() {
        AuthProperties properties = properties(SECRET, Duration.ofMinutes(15));
        JwtService issuer = new JwtService(
                authConfig.jwtEncoder(properties), decoderValidatingAt(NOW), properties, clock);

        String token = issuer.issueAccessToken(42L, Role.ADMIN);
        assertThat(issuer.readAccessToken(token)).isPresent();

        JwtService oneHourLater = new JwtService(
                authConfig.jwtEncoder(properties),
                decoderValidatingAt(NOW.plus(Duration.ofHours(1))),
                properties,
                clock);

        assertThat(oneHourLater.readAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        AuthProperties otherIssuer = new AuthProperties(
                "http://localhost:5173",
                new AuthProperties.Jwt(SECRET, "somewhere-else", Duration.ofMinutes(15), Duration.ofDays(30)),
                new AuthProperties.Cookie(true, "Lax"),
                otp());

        String token = new JwtService(
                authConfig.jwtEncoder(otherIssuer), decoderValidatingAt(NOW), otherIssuer, clock)
                .issueAccessToken(42L, Role.ADMIN);

        assertThat(jwtService(Duration.ofMinutes(15)).readAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwtService(Duration.ofMinutes(15)).readAccessToken("not-a-jwt")).isEmpty();
    }

    private JwtService jwtService(Duration accessTokenTtl) {
        AuthProperties properties = properties(SECRET, accessTokenTtl);
        JwtEncoder encoder = authConfig.jwtEncoder(properties);
        return new JwtService(encoder, decoderValidatingAt(NOW), properties, clock);
    }

    private JwtDecoder decoderValidatingAt(Instant validationTime) {
        return decoderValidatingAt(SECRET, validationTime);
    }

    private JwtDecoder decoderValidatingAt(String secret, Instant validationTime) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(Clock.fixed(validationTime, ZoneOffset.UTC));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    private AuthProperties properties(String secret, Duration accessTokenTtl) {
        return new AuthProperties(
                "http://localhost:5173",
                new AuthProperties.Jwt(secret, ISSUER, accessTokenTtl, Duration.ofDays(30)),
                new AuthProperties.Cookie(true, "Lax"),
                otp());
    }

    private AuthProperties.Otp otp() {
        return new AuthProperties.Otp(
                Duration.ofMinutes(5),
                6,
                5,
                "console",
                new AuthProperties.RateLimit(3, 10, 10, 30, Duration.ofMinutes(15)));
    }
}
