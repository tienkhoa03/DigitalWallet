/**
 * Cross-cutting security primitives: password hashing, JWT issuance, role enum.
 *
 * <p>Verification of JWTs is handled by {@code quarkus-smallrye-jwt} reading
 * {@code mp.jwt.verify.*} properties. This package owns the issuer side and the
 * Argon2id hasher only.
 */
package com.digitalwallet.shared.security;
