package com.digitalwallet.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Argon2Hasher} — round-trip, format, negative cases.
 *
 * <p>Uses a reduced memory cost (4 MiB) so the test runs fast; production keeps
 * 64 MiB per {@code .claude/rules/security.md §2}.
 */
class Argon2HasherTest {

    private Argon2Hasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new Argon2Hasher(4096, 2, 1);
        hasher.validate();
    }

    @Test
    void hash_thenVerify_withSamePassword_returnsTrue() {
        String stored = hasher.hash("correct horse battery staple");

        assertThat(hasher.verify("correct horse battery staple", stored)).isTrue();
    }

    @Test
    void hash_producesSelfDescribingPhcFormat() {
        String stored = hasher.hash("anything-12-chars");

        assertThat(stored).startsWith("$argon2id$v=19$m=4096,t=2,p=1$");
        assertThat(stored.split("\\$")).hasSize(6);
    }

    @Test
    void hash_withSamePasswordTwice_producesDifferentHashes() {
        String first = hasher.hash("password-12chr");
        String second = hasher.hash("password-12chr");

        assertThat(first).isNotEqualTo(second); // different salt
        assertThat(hasher.verify("password-12chr", first)).isTrue();
        assertThat(hasher.verify("password-12chr", second)).isTrue();
    }

    @Test
    void verify_withWrongPassword_returnsFalse() {
        String stored = hasher.hash("correct horse battery staple");

        assertThat(hasher.verify("wrong-password", stored)).isFalse();
    }

    @Test
    void verify_withTamperedHashString_returnsFalse() {
        String stored = hasher.hash("correct horse battery staple");
        // flip a character inside the base64 hash section
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThat(hasher.verify("correct horse battery staple", tampered)).isFalse();
    }

    @Test
    void verify_withMalformedHashString_returnsFalseWithoutThrowing() {
        assertThat(hasher.verify("password", "not-a-hash")).isFalse();
        assertThat(hasher.verify("password", "$argon2id$broken")).isFalse();
        assertThat(hasher.verify("password", "$argon2id$v=19$m=bad,t=3,p=1$AAAA$BBBB")).isFalse();
    }

    @Test
    void verify_withNullInputs_returnsFalse() {
        String stored = hasher.hash("password-12-chars");

        assertThat(hasher.verify(null, stored)).isFalse();
        assertThat(hasher.verify("password-12-chars", null)).isFalse();
        assertThat(hasher.verify(null, null)).isFalse();
    }

    @Test
    void verify_acrossDifferentParameters_stillWorks_whenStoredHashEmbedsParameters() {
        // Hash with a smaller param set, then verify with a hasher configured differently.
        // The stored format carries m/t/p, so verify must re-derive using stored values.
        String stored = new Argon2Hasher(2048, 1, 1).hash("twelve-chars-please");
        Argon2Hasher otherHasher = new Argon2Hasher(8192, 3, 1);

        assertThat(otherHasher.verify("twelve-chars-please", stored)).isTrue();
    }
}
