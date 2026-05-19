package com.digitalwallet.shared.outbox;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.List;

/**
 * NFR2 — single producer to Kafka. The HTTP request thread MUST NEVER publish directly
 * (backend_coding.md §2, §15). This poller is the only path.
 *
 * <p>Phase C skeleton: drains rows and marks them published. Wiring to the actual Kafka
 * {@code @Channel} emitter lands in the wallet feature PR, alongside the
 * {@code transaction-events} consumer contract (CLAUDE.md "Asynchronous stream").
 */
@ApplicationScoped
public class OutboxPoller {

    private static final Logger LOG = Logger.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxPoller(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repository.findUnpublished(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                publish(event);
                event.markPublished(clock.instant());
            } catch (RuntimeException ex) {
                LOG.errorf(ex, "Outbox publish failed for event id=%s eventType=%s",
                        event.id(), event.eventType());
                throw ex;
            }
        }
    }

    /**
     * Wires to SmallRye {@code @Channel} in a later PR (Wallet / Fraud feature integration).
     * Kept as a method so tests can override it; default is a no-op.
     */
    protected void publish(OutboxEvent event) {
        // intentional no-op until the channel mapping lands per CLAUDE.md "Asynchronous stream"
    }
}
