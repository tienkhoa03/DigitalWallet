package com.digitalwallet.user.api;

import com.digitalwallet.shared.security.JwtIssuer;
import com.digitalwallet.shared.security.UserRole;
import com.digitalwallet.testsupport.PostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.restassured.RestAssured.given;

/**
 * End-to-end JWT verifier wiring test — exercises the test-only protected resource at
 * {@code /_test/protected} with a sequence of valid and forged tokens. Verifies the
 * full Phase 1 plan §10 acceptance set.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class JwtVerifierIT {

    @Inject
    JwtIssuer issuer;

    @Test
    void protectedEndpoint_withFreshlyIssuedToken_returns200() {
        String token = issuer.issue(UUID.randomUUID(), UserRole.USER);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(200);
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        given()
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withAlgNoneToken_returns401() {
        // Build an unsigned token (header alg=none, empty signature).
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        long now = Instant.now().getEpochSecond();
        String payload = base64Url(("""
                {"sub":"%s","iss":"digitalwallet","aud":"digitalwallet-api",
                 "iat":%d,"exp":%d,"groups":["USER"]}""")
                .formatted(UUID.randomUUID(), now, now + 3600));
        String token = header + "." + payload + ".";

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withHs256TokenForgedAgainstPublicKey_returns401() throws Exception {
        // Forge an HS256-signed token using the literal "secret" string. The verifier is
        // pinned to ES256 by smallrye.jwt.verify.algorithm so this MUST be rejected.
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long now = Instant.now().getEpochSecond();
        String payload = base64Url(("""
                {"sub":"%s","iss":"digitalwallet","aud":"digitalwallet-api",
                 "iat":%d,"exp":%d,"groups":["USER"]}""")
                .formatted(UUID.randomUUID(), now, now + 3600));
        String signingInput = header + "." + payload;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("any-shared-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        String token = signingInput + "." + sig;

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() {
        // Issue a token whose iat/exp are far in the past — beyond the 30s skew.
        Instant past = Instant.now().minusSeconds(7200);
        String token = Jwt.claims()
                .subject(UUID.randomUUID().toString())
                .issuer("digitalwallet")
                .audience("digitalwallet-api")
                .groups(Set.of("USER"))
                .issuedAt(past.getEpochSecond())
                .expiresAt(past.plusSeconds(60).getEpochSecond())
                .jws()
                .algorithm(SignatureAlgorithm.ES256)
                .sign(readTestSigningKey());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withWrongIssuer_returns401() {
        Instant now = Instant.now();
        String token = Jwt.claims()
                .subject(UUID.randomUUID().toString())
                .issuer("not-digitalwallet")
                .audience("digitalwallet-api")
                .groups(Set.of("USER"))
                .issuedAt(now.getEpochSecond())
                .expiresAt(now.plusSeconds(3600).getEpochSecond())
                .jws()
                .algorithm(SignatureAlgorithm.ES256)
                .sign(readTestSigningKey());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withWrongAudience_returns401() {
        Instant now = Instant.now();
        String token = Jwt.claims()
                .subject(UUID.randomUUID().toString())
                .issuer("digitalwallet")
                .audience("some-other-api")
                .groups(Set.of("USER"))
                .issuedAt(now.getEpochSecond())
                .expiresAt(now.plusSeconds(3600).getEpochSecond())
                .jws()
                .algorithm(SignatureAlgorithm.ES256)
                .sign(readTestSigningKey());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/_test/protected")
                .then()
                .statusCode(401);
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.replaceAll("\\s+", "").getBytes(StandardCharsets.UTF_8));
    }

    private java.security.PrivateKey readTestSigningKey() {
        try (var in = JwtVerifierIT.class.getResourceAsStream("/META-INF/jwt-private-key-test.pem")) {
            if (in == null) {
                throw new IllegalStateException("Missing /META-INF/jwt-private-key-test.pem");
            }
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return java.security.KeyFactory.getInstance("EC")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
