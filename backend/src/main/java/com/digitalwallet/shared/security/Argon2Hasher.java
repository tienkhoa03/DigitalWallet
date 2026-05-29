package com.digitalwallet.shared.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Argon2id password hasher per OWASP 2024 minimums (Open Q #1).
 *
 * <p>Stored format: {@code $argon2id$v=19$m=<KiB>,t=<iter>,p=<par>$<base64-salt>$<base64-hash>}.
 * The format is self-describing so the {@link #verify(String, String)} call re-derives
 * using the parameters embedded in the stored hash — future parameter bumps do not require
 * a backfill.
 *
 * <p>Parameters resolve from {@code app.argon2.*} so the test profile can lower the memory
 * cost. Production keeps the OWASP 64 MiB minimum.
 *
 * <p>Comparison uses {@link MessageDigest#isEqual(byte[], byte[])} for constant-time equality
 * — see {@code .claude/rules/security.md §2}.
 */
@ApplicationScoped
public class Argon2Hasher {

    private static final int ARGON2_VERSION = Argon2Parameters.ARGON2_VERSION_13; // v=19
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private final int memoryKb;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom random = new SecureRandom();

    public Argon2Hasher(
            @ConfigProperty(name = "app.argon2.memory-kb", defaultValue = "65536") int memoryKb,
            @ConfigProperty(name = "app.argon2.iterations", defaultValue = "3") int iterations,
            @ConfigProperty(name = "app.argon2.parallelism", defaultValue = "1") int parallelism) {
        this.memoryKb = memoryKb;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @PostConstruct
    void validate() {
        if (memoryKb < 1024) {
            throw new IllegalStateException(
                    "Argon2 memory cost too low for safety: " + memoryKb + " KiB (minimum 1 MiB)");
        }
        if (iterations < 1 || parallelism < 1) {
            throw new IllegalStateException("Argon2 iterations and parallelism must be >= 1");
        }
    }

    /**
     * Hash a plaintext password using the configured parameters.
     *
     * @return the self-describing PHC-format string
     */
    public String hash(String password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, memoryKb, iterations, parallelism, HASH_LENGTH);

        Base64.Encoder enc = Base64.getEncoder().withoutPadding();
        // ARGON2_VERSION_13 is the constant 0x13 (19 decimal) — the standard v=19 wire value.
        return "$argon2id$v=" + ARGON2_VERSION
                + "$m=" + memoryKb + ",t=" + iterations + ",p=" + parallelism
                + "$" + enc.encodeToString(salt)
                + "$" + enc.encodeToString(hash);
    }

    /**
     * Constant-time verify of {@code password} against a stored hash. Returns {@code false}
     * for any unparseable or null input; never throws on malformed stored values.
     */
    public boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        Parsed parsed;
        try {
            parsed = Parsed.parse(stored);
        } catch (RuntimeException e) {
            return false;
        }
        byte[] candidate = derive(password, parsed.salt, parsed.memoryKb, parsed.iterations,
                parsed.parallelism, parsed.hash.length);
        return MessageDigest.isEqual(candidate, parsed.hash);
    }

    private static byte[] derive(String password, byte[] salt, int memoryKb, int iterations,
                                 int parallelism, int outputLength) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(ARGON2_VERSION)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] out = new byte[outputLength];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }

    private record Parsed(int memoryKb, int iterations, int parallelism, byte[] salt, byte[] hash) {

        static Parsed parse(String stored) {
            // Expected: $argon2id$v=19$m=65536,t=3,p=1$<salt>$<hash>
            String[] parts = stored.split("\\$");
            if (parts.length != 6) {
                throw new IllegalArgumentException("malformed argon2 hash");
            }
            if (!"argon2id".equals(parts[1])) {
                throw new IllegalArgumentException("not an argon2id hash");
            }
            // parts[2] is "v=19" — we accept v=19 only.
            if (!parts[2].startsWith("v=")) {
                throw new IllegalArgumentException("missing version marker");
            }
            // parts[3] is "m=<n>,t=<n>,p=<n>"
            int m = 0, t = 0, p = 0;
            for (String kv : parts[3].split(",")) {
                String[] e = kv.split("=");
                if (e.length != 2) {
                    throw new IllegalArgumentException("malformed parameter section");
                }
                switch (e[0]) {
                    case "m" -> m = Integer.parseInt(e[1]);
                    case "t" -> t = Integer.parseInt(e[1]);
                    case "p" -> p = Integer.parseInt(e[1]);
                    default -> throw new IllegalArgumentException("unknown parameter " + e[0]);
                }
            }
            if (m <= 0 || t <= 0 || p <= 0) {
                throw new IllegalArgumentException("non-positive parameter");
            }
            Base64.Decoder dec = Base64.getDecoder();
            byte[] salt = dec.decode(parts[4]);
            byte[] hash = dec.decode(parts[5]);
            return new Parsed(m, t, p, salt, hash);
        }
    }
}
