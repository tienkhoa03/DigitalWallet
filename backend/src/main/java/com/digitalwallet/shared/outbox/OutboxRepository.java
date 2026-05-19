package com.digitalwallet.shared.outbox;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * NFR2 — outbox draining. Backend_coding.md §15: poller reads unpublished rows in
 * {@code created_at ASC} order.
 */
@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    public List<OutboxEvent> findUnpublished(int batchSize) {
        return find("publishedAt is null", Sort.by("createdAt"))
                .page(0, batchSize)
                .list();
    }
}
