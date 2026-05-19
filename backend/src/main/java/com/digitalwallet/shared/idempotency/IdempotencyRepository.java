package com.digitalwallet.shared.idempotency;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/**
 * Per backend_coding.md §5: Panache Repository pattern returning {@link Optional} on single-row
 * lookups. Feature modules use this to check / persist NFR3 replay records.
 */
@ApplicationScoped
public class IdempotencyRepository
        implements PanacheRepositoryBase<IdempotencyRecord, IdempotencyRecord.PK> {

    public Optional<IdempotencyRecord> findByKey(UUID idempotencyKey, UUID accountId) {
        return find("idempotencyKey = ?1 and accountId = ?2", idempotencyKey, accountId).firstResultOptional();
    }
}
