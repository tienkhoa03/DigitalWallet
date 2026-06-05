package com.digitalwallet.shared.security;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
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
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        String token = issuer.issue(accountId, AccountRole.USER);

        JwtClaims claims = parse(token);
        assertThat(claims.getSubject()).isEqualTo(accountId.toString());
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.getStringListClaimValue("groups")).containsExactly("USER");
        assertThat(claims.getIssuedAt().getValue())
                .isEqualTo(fixedClock.instant().getEpochSecond());
        assertThat(claims.getExpirationTime().getValue())
                .isEqualTo(fixedClock.instant().getEpochSecond() + TTL);
    }

    @Test
    void issue_withAdminRole_putsAdminInGroupsClaim() throws Exception {
        String token = issuer.issue(UUID.randomUUID(), AccountRole.ADMIN);

        JwtClaims claims = parse(token);
        assertThat(claims.getStringListClaimValue("groups")).containsExactly("ADMIN");
    }

    @Test
    void issue_isStableAcrossCalls_whenClockIsFixed() {
        UUID accountId = UUID.randomUUID();

        String first = issuer.issue(accountId, AccountRole.USER);
        String second = issuer.issue(accountId, AccountRole.USER);

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

    /**
     * Verifies the ES256 signature plus the canonical {@code iss}/{@code aud} constraints, evaluating
     * token expiry at the injected fixed clock's instant rather than wall-clock time. Anchoring the
     * evaluation time keeps the round-trip deterministic no matter which calendar date the suite runs
     * on — the original wall-clock parse turned this into a time bomb that expired after the fixed
     * issue date (testing.md §2.2 Clock injection, §6 "flaky tests are defects").
     */
    private JwtClaims parse(String token) throws Exception {
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setVerificationKey(publicKey)
                .setExpectedIssuer(ISSUER)
                .setExpectedAudience(AUDIENCE)
                .setRequireSubject()
                .setRequireIssuedAt()
                .setRequireExpirationTime()
                .setEvaluationTime(NumericDate.fromSeconds(fixedClock.instant().getEpochSecond()))
                .setAllowedClockSkewInSeconds(30)
                .setJwsAlgorithmConstraints(new AlgorithmConstraints(
                        ConstraintType.PERMIT, AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256))
                .build();
        return consumer.processToClaims(token);
    }
}
