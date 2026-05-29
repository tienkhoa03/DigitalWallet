package com.digitalwallet.user.persistence;

import com.digitalwallet.shared.security.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code "user"} table created in Flyway V1.
 *
 * <p>The table name is quoted because {@code user} is a Postgres reserved word — see
 * {@code docs/database/README.md} and the Phase 1 plan §5. {@code fraud_status} is
 * persisted as a varchar (no enum mapping yet; the post-MVP fraud module will refine).
 *
 * <p>Public-field Panache idiom is intentional: this is a record-shaped entity used only
 * by {@link UserRepository} and the {@code user/service/} layer. JAX-RS resources MUST
 * NOT return this type directly ({@code .claude/rules/backend_coding.md §6}).
 */
@Entity
@Table(name = "\"user\"")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    public UserRole role;

    @Column(name = "base_currency", nullable = false, length = 3)
    public String baseCurrency;

    @Column(name = "fraud_status", nullable = false)
    public String fraudStatus;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
