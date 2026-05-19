package com.digitalwallet.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * NFR2 — Transactional Outbox row. Schema: V1__init_schema.sql (outbox_event).
 * Ledger commit + outbox row land in a single DB transaction; the {@link OutboxPoller}
 * is the ONLY producer to Kafka (backend_coding.md §15).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId,
                       String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID id()                 { return id; }
    public String aggregateType()    { return aggregateType; }
    public UUID aggregateId()        { return aggregateId; }
    public String eventType()        { return eventType; }
    public String payload()          { return payload; }
    public Instant createdAt()       { return createdAt; }
    public Instant publishedAt()     { return publishedAt; }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }
}
