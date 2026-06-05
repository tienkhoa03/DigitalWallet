package com.digitalwallet.account.persistence;

import com.digitalwallet.shared.security.AccountRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code account} table created in Flyway V1.
 *
 * <p>{@code account} is a plain identifier in Postgres (unlike the old {@code "user"}
 * name it replaces, it is not a reserved word), so the table needs no quoting — see
 * {@code docs/database/README.md} and the Phase 1 plan §5. {@code fraud_status} is
 * persisted as a varchar (no enum mapping yet; the post-MVP fraud module will refine).
 *
 * <p>Public-field Panache idiom is intentional: this is a record-shaped entity used only
 * by {@link AccountRepository} and the {@code account/service/} layer. JAX-RS resources MUST
 * NOT return this type directly ({@code .claude/rules/backend_coding.md §6}).
 */
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    public AccountRole role;

    @Column(name = "base_currency", nullable = false, length = 3)
    public String baseCurrency;

    @Column(name = "fraud_status", nullable = false)
    public String fraudStatus;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
