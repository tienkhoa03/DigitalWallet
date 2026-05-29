package com.digitalwallet.shared.security;

import io.quarkus.runtime.Startup;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Parses the {@code app.jwt.private-key} PEM once at startup into a usable
 * {@link PrivateKey} for {@link JwtIssuer} to sign with.
 *
 * <p>The env-var form supports literal {@code \n} sequences in place of physical newlines
 * so 12-factor-style configs can keep the key on a single line. Startup fails fast if the
 * value is missing or unparseable — there is no fallback to a default key.
 */
@Startup
@ApplicationScoped
public class JwtSigningKeyProvider {

    private static final Logger LOG = Logger.getLogger(JwtSigningKeyProvider.class);

    private final JwtSigningConfig config;
    private PrivateKey privateKey;

    public JwtSigningKeyProvider(JwtSigningConfig config) {
        this.config = config;
    }

    @PostConstruct
    void init() {
        String raw = config.privateKey().orElse("");
        if (raw.isBlank()) {
            // We do not eagerly throw here — some tests boot without needing the issuer.
            // JwtIssuer.issue(...) will surface the same problem with a clearer message
            // when a token is actually requested.
            LOG.warn("app.jwt.private-key is not configured; JwtIssuer will fail when used");
            return;
        }
        this.privateKey = parse(raw);
        LOG.info("JWT signing key loaded");
    }

    /**
     * @return the parsed EC private key
     * @throws IllegalStateException if the key was not configured at startup
     */
    public PrivateKey getPrivateKey() {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "JWT signing key is not configured (set JWT_PRIVATE_KEY env var)");
        }
        return privateKey;
    }

    private static PrivateKey parse(String raw) {
        String normalised = raw.replace("\\n", "\n");
        String base64 = normalised
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception e) {
            // Do not include the raw key material in the exception or logs.
            throw new IllegalStateException("Failed to parse JWT private key as PKCS#8 EC", e);
        }
    }
}
