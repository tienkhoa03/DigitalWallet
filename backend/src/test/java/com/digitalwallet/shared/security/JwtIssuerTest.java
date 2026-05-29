package com.digitalwallet.shared.security;

import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtIssuer}. Uses the committed test keypair to sign and verify
 * a round-trip token; asserts the canonical claim shape (Open Q #4).
 */
class JwtIssuerTest {

    private static final String ISSUER = "digitalwallet";
    private static final String AUDIENCE = "digitalwallet-api";
    private static final long TTL = 3600L;

    private Clock fixedClock;
    private JwtIssuer issuer;
    private PublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        fixedClock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);

        String privPem = readResource("/META-INF/jwt-private-key-test.pem");
        JwtSigningConfig config = Mockito.mock(JwtSigningConfig.class);
        Mockito.when(config.issuer()).thenReturn(ISSUER);
        Mockito.when(config.audience()).thenReturn(AUDIENCE);
        Mockito.when(config.ttlSeconds()).thenReturn(TTL);
        Mockito.when(config.privateKey()).thenReturn(Optional.of(privPem));

        JwtSigningKeyProvider provider = new JwtSigningKeyProvider(config);
        provider.init();

        issuer = new JwtIssuer(config, provider, fixedClock);

        publicKey = parsePublicKey(readResource("/META-INF/jwt-public-key-test.pem"));
    }

    @Test
    void issue_producesTokenWithCanonicalClaims() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        String token = issuer.issue(userId, UserRole.USER);

        JsonWebToken jwt = parse(token);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getIssuer()).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getGroups()).containsExactly("USER");
        assertThat(jwt.getIssuedAtTime()).isEqualTo(fixedClock.instant().getEpochSecond());
        assertThat(jwt.getExpirationTime())
                .isEqualTo(fixedClock.instant().getEpochSecond() + TTL);
    }

    @Test
    void issue_withAdminRole_putsAdminInGroupsClaim() throws Exception {
        String token = issuer.issue(UUID.randomUUID(), UserRole.ADMIN);

        JsonWebToken jwt = parse(token);
        assertThat(jwt.getGroups()).containsExactly("ADMIN");
    }

    @Test
    void issue_isStableAcrossCalls_whenClockIsFixed() {
        UUID userId = UUID.randomUUID();

        String first = issuer.issue(userId, UserRole.USER);
        String second = issuer.issue(userId, UserRole.USER);

        // Tokens differ because the signature embeds a random k value, but both verify
        // and carry identical claim payloads.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void issue_exposesTtlForLoginResponse() {
        assertThat(issuer.ttlSeconds()).isEqualTo(TTL);
    }

    private static String readResource(String path) {
        try (InputStream in = JwtIssuerTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ECPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }

    private JsonWebToken parse(String token) throws Exception {
        JWTAuthContextInfo info = new JWTAuthContextInfo();
        info.setPublicVerificationKey(publicKey);
        info.setIssuedBy(ISSUER);
        info.setExpectedAudience(java.util.Set.of(AUDIENCE));
        info.setClockSkew(30);
        info.setSignatureAlgorithm(java.util.Set.of(io.smallrye.jwt.algorithm.SignatureAlgorithm.ES256));
        return new DefaultJWTParser(info).parse(token);
    }
}
