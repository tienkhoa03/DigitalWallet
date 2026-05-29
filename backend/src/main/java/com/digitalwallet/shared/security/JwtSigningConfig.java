package com.digitalwallet.shared.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Issuer-side JWT configuration. Mirrors the verifier-side {@code mp.jwt.verify.*}
 * properties so {@link JwtIssuer} stamps the same {@code iss} / {@code aud} that the
 * verifier accepts.
 *
 * <p>The private key is supplied via the {@code JWT_PRIVATE_KEY} env var in prod (PEM with
 * literal {@code \n} escapes for newlines). Test profile overrides via
 * {@code %test.app.jwt.private-key} pointing at the committed test private key.
 */
@ConfigMapping(prefix = "app.jwt")
public interface JwtSigningConfig {

    String issuer();

    String audience();

    @WithDefault("3600")
    long ttlSeconds();

    /**
     * PEM-encoded EC private key (PKCS#8). Optional — startup fails fast in
     * {@link JwtSigningKeyProvider} only when {@link #issuer()} is exercised and the value
     * is blank.
     */
    Optional<String> privateKey();
}
