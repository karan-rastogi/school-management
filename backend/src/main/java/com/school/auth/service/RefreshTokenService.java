package com.school.auth.service;

import com.school.auth.config.AuthProperties;
import com.school.auth.domain.RefreshToken;
import com.school.auth.repository.RefreshTokenRepository;
import com.school.user.domain.Role;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public IssuedToken issue(Long userId, Role activeRole) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(authProperties.jwt().refreshTokenTtl());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setActiveRole(activeRole);
        refreshTokenRepository.saveAndFlush(refreshToken);

        return new IssuedToken(rawToken, expiresAt);
    }

    public Optional<Rotated> rotate(String rawToken) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken refreshToken = found.get();
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(refreshToken.getUserId(), now);
            return Optional.empty();
        }

        if (!refreshToken.isUsable(now)) {
            return Optional.empty();
        }

        refreshToken.setRevokedAt(now);
        refreshTokenRepository.saveAndFlush(refreshToken);

        Long userId = refreshToken.getUserId();
        Role activeRole = refreshToken.getActiveRole();

        return Optional.of(new Rotated(userId, activeRole, issue(userId, activeRole)));
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(refreshToken -> {
            if (!refreshToken.isRevoked()) {
                refreshToken.setRevokedAt(OffsetDateTime.now(clock));
                refreshTokenRepository.saveAndFlush(refreshToken);
            }
        });
    }

    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now(clock));
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record IssuedToken(String rawToken, OffsetDateTime expiresAt) {
    }

    public record Rotated(Long userId, Role activeRole, IssuedToken issued) {
    }
}
