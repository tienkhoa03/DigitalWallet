# 0007 — Two-stream architecture (sync core banking, async fraud)

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

The product spec is built around a deliberate split: a synchronous, ACID-correct money-movement path, and an asynchronous fraud-analysis path that consumes the events emitted by the first ([../../README.md §1, §5, NFR5](../../README.md)). This split is the architectural backbone — every other decision (Kafka, idempotency, locking, outbox) flows from it.

## Decision

We will keep the request thread small and synchronous: validate, lock, commit, publish, return. We will run all fraud analysis on Kafka consumer threads, with no synchronous calls back into the request path. The admin dashboard receives alerts over WebSocket from the `fraud-alerts` stream — also asynchronous from the user's perspective.

## Options considered

### Option A — Single in-process pipeline
- Pros: simple; one deployable unit; no broker.
- Cons: violates NFR5; couples request-path latency to risk-analysis cost.

### Option B — Two streams via Kafka  *(chosen)*
- Pros: matches the spec; each stream can fail and scale independently; rule changes do not touch the API; aligns with the demo's stated learning goals.
- Cons: more moving parts; introduces consistency questions resolved in [0006](0006-outbox-or-publish-after-commit.md).

### Option C — Two services (separate processes), no broker
- Pros: physical isolation.
- Cons: needs synchronous API between them — the same coupling problem as Option A, just over the network.

## Consequences

- Easier: independently evolving the two streams; demoing the value of decoupling.
- Harder: operating two stateful subsystems (DB, broker) in lockstep.
- Live with: the broker as a load-bearing piece of infrastructure.
- Revisit if: the project's scope shrinks to a single-stream demo where the broker is no longer worth its weight.

## References

- [../../README.md §1, §5, NFR5](../../README.md).
- [../architecture/README.md](../architecture/README.md).
- [0003](0003-kafka-decouples-fraud-engine.md).
