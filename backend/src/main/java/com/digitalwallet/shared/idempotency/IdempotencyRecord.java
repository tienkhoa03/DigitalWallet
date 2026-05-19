package com.digitalwallet.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * NFR3 — replay protection. Schema: V1__init_schema.sql (idempotency_record).
 * Primary key is the composite (idempotency_key, account_id) — same key from a different
 * caller is a separate record (no key-collision attack across accounts).
 */
@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecord.PK.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID idempotencyKey, UUID accountId, String requestHash,
                             int responseStatus, String responseBody, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.accountId = accountId;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public UUID idempotencyKey() { return idempotencyKey; }
    public UUID accountId()       { return accountId; }
    public String requestHash()   { return requestHash; }
    public int responseStatus()   { return responseStatus; }
    public String responseBody()  { return responseBody; }
    public Instant createdAt()    { return createdAt; }

    public static final class PK implements java.io.Serializable {
        private UUID idempotencyKey;
        private UUID accountId;

        public PK() {}
        public PK(UUID idempotencyKey, UUID accountId) {
            this.idempotencyKey = idempotencyKey;
            this.accountId = accountId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(idempotencyKey, pk.idempotencyKey)
                    && Objects.equals(accountId, pk.accountId);
        }
        @Override public int hashCode() {
            return Objects.hash(idempotencyKey, accountId);
        }
    }
}
