package com.digitalwallet.shared.audit;

import com.digitalwallet.shared.exception.AuditFailureException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Cross-cutting audit append helper.
 * Failure to write surfaces {@link AuditFailureException} ({@code audit.write_failed},
 * HTTP 500 per backend_coding.md §8) — the call site MUST treat the audit row as a
 * release-blocking step, not a fire-and-forget side effect.
 */
@ApplicationScoped
public class AuditLogWriter {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogWriter(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void append(UUID actorAccountId, UUID subjectAccountId, String action, String detailsJson) {
        try {
            AuditLog row = new AuditLog(actorAccountId, subjectAccountId, action,
                    detailsJson == null ? "{}" : detailsJson, clock.instant());
            repository.persist(row);
        } catch (RuntimeException ex) {
            throw new AuditFailureException("Failed to append audit row for action " + action, ex);
        }
    }
}
