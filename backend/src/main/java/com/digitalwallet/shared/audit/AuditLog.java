package com.digitalwallet.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit-log row. Schema: V1__init_schema.sql (audit_log).
 * Required for auth events, admin reads, fraud-driven blocks, account suspension —
 * .claude/rules/security.md §2, §3 and project-info.md §8.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "subject_account_id")
    private UUID subjectAccountId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID actorAccountId, UUID subjectAccountId, String action,
                    String details, Instant createdAt) {
        this.actorAccountId = actorAccountId;
        this.subjectAccountId = subjectAccountId;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long id()                   { return id; }
    public UUID actorAccountId()       { return actorAccountId; }
    public UUID subjectAccountId()     { return subjectAccountId; }
    public String action()             { return action; }
    public String details()            { return details; }
    public Instant createdAt()         { return createdAt; }
}
