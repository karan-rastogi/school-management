package com.school.auth.security;

import com.school.auth.config.AuthProperties;
import com.school.user.domain.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    public static final String ACTIVE_ROLE_CLAIM = "act";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, AuthProperties authProperties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public String issueAccessToken(Long userId, Role activeRole) {
        Instant now = Instant.now(clock);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(authProperties.jwt().issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.jwt().accessTokenTtl()))
                .id(UUID.randomUUID().toString());

        if (activeRole != null) {
            claims.claim(ACTIVE_ROLE_CLAIM, activeRole.name());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public Optional<AccessTokenClaims> readAccessToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Long userId = Long.valueOf(jwt.getSubject());
            String activeRole = jwt.getClaimAsString(ACTIVE_ROLE_CLAIM);
            return Optional.of(new AccessTokenClaims(
                    userId,
                    activeRole == null ? null : Role.valueOf(activeRole)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(Long userId, Role activeRole) {
    }
}
