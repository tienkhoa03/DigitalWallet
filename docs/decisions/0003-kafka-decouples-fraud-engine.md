# 0003 — Kafka decouples the fraud engine from the transaction path

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

[NFR5](../../README.md) requires that the transfer API does only two things on the request thread: persist the transfer and publish an event. Risk analysis must run elsewhere. [§5 of the spec](../../README.md) names two topics, `transaction-events` and `fraud-alerts`, and assigns the fraud engine to a separate consumer thread.

## Decision

We will route every successful transaction through Kafka topic `transaction-events`. The fraud engine subscribes to that topic and publishes alerts to `fraud-alerts`. The transfer request thread never invokes fraud logic.

## Options considered

### Option A — In-process synchronous fraud check
- Pros: simplest implementation; no broker.
- Cons: violates NFR5; rule latency adds to every transfer; a rule bug crashes the request path.

### Option B — In-process async (executor / queue)
- Pros: no broker; some isolation.
- Cons: shares the JVM with the API — backpressure or memory pressure in the rule engine still hurts the transaction path; doesn't survive restarts.

### Option C — Kafka  *(chosen)*
- Pros: durable buffer; consumer can scale independently; survives restarts; idiomatic for event-driven architecture; demo-ready.
- Cons: operational overhead of a broker; introduces the commit-vs-publish consistency problem (addressed by [0006](0006-outbox-or-publish-after-commit.md)).

## Consequences

- Easier: changing or extending fraud rules without touching the API; replaying historical events through new rules.
- Harder: ensuring DB commit and Kafka publish do not silently diverge — the topic is now load-bearing.
- Live with: a broker in the local Compose stack.
- Revisit if: the project's deployment topology becomes hostile to running Kafka.

## References

- [../../README.md NFR5, §5](../../README.md).
- [0006](0006-outbox-or-publish-after-commit.md), [0007](0007-two-stream-architecture.md).
- [../business-rules/fraud-rules.md](../business-rules/fraud-rules.md).
