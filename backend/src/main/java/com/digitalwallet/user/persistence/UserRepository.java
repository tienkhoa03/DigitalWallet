package com.digitalwallet.user.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/**
 * Panache repository for {@link UserEntity}.
 *
 * <p>{@link #findByEmailLower(String)} is the canonical lookup — Open Q #10 mandates
 * case-insensitive uniqueness, so callers MUST pass the already-lowercased email.
 * The JPQL uses a bound parameter; no string concatenation
 * ({@code .claude/rules/security.md §4}).
 */
@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

    /**
     * Case-insensitive lookup. The caller is responsible for lowercasing the input;
     * the query lowercases the column value on the database side.
     */
    public Optional<UserEntity> findByEmailLower(String emailLower) {
        return find("LOWER(email) = ?1", emailLower).firstResultOptional();
    }
}
