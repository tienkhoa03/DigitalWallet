package com.digitalwallet.shared.security;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Issues ES256 JWTs carrying the claim shape committed in the Phase 1 plan (Open Q #4):
 *
 * <ul>
 *   <li>{@code sub} = user UUID as string</li>
 *   <li>{@code iss} = {@code app.jwt.issuer}</li>
 *   <li>{@code aud} = single element {@code [app.jwt.audience]}</li>
 *   <li>{@code groups} = single role name, the SmallRye-JWT convention for
 *       {@code @RolesAllowed(...)}</li>
 *   <li>{@code iat} = injected {@link Clock#instant()}</li>
 *   <li>{@code exp} = {@code iat + app.jwt.ttl-seconds}</li>
 * </ul>
 *
 * <p>{@link Clock} is injected so time-dependent tests run against a fixed instant; this
 * is required by {@code .claude/rules/upgrade-policy.md §3}.
 */
@ApplicationScoped
public class JwtIssuer {

    private final JwtSigningConfig config;
    private final JwtSigningKeyProvider keyProvider;
    private final Clock clock;

    public JwtIssuer(JwtSigningConfig config, JwtSigningKeyProvider keyProvider, Clock clock) {
        this.config = config;
        this.keyProvider = keyProvider;
        this.clock = clock;
    }

    public String issue(UUID userId, UserRole role) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(config.ttlSeconds());

        return Jwt.claims()
                .subject(userId.toString())
                .issuer(config.issuer())
                .audience(config.audience())
                .groups(Set.of(role.name()))
                .issuedAt(now.getEpochSecond())
                .expiresAt(exp.getEpochSecond())
                .jws()
                .algorithm(SignatureAlgorithm.ES256)
                .sign(keyProvider.getPrivateKey());
    }

    /**
     * @return the token TTL in seconds — exposed for the login response's
     *     {@code expires_in} field
     */
    public long ttlSeconds() {
        return config.ttlSeconds();
    }
}
