# 0006 — DB-Kafka consistency: Outbox or commit-then-publish

- **Status**: Proposed
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

[NFR2](../../README.md) requires that committing the DB transaction and publishing to Kafka do not "result in synchronization failures." There are two reasonable strategies; the spec does not pick one.

## Decision

We will start with **commit-then-publish** ordered inside the request handler, returning success only after both the commit and the publish have succeeded. We will move to a full **Outbox Pattern** (events written to an `outbox_events` table inside the same transaction, drained asynchronously) once the demo's reliability requirements push past what commit-then-publish can offer.

## Options considered

### Option A — Publish-then-commit
- Pros: trivial.
- Cons: a publish that succeeds before a commit failure leaves a "ghost transfer" event with no DB record. The hardest failure mode to recover from.

### Option B — Commit-then-publish  *(initial choice)*
- Pros: at-least-once delivery via client retries; matches NFR3's idempotency story; no extra schema.
- Cons: if the process dies between commit and publish, the event is lost until the client retries with the same `Idempotency-Key`. Acceptable because the retry surfaces the missing event.

### Option C — Outbox Pattern  *(target)*
- Pros: atomic with the transaction; survives crashes; the cleanest answer to NFR2.
- Cons: extra table, extra drainer process, extra moving parts; more code than the MVP needs on day one.

## Consequences

- Easier (now): implementation; no schema additions for the outbox.
- Harder (later): every change to event content also needs to consider the outbox migration when we move to Option C.
- Live with: a small window where a process crash drops an event until the client retries.
- Revisit if: we observe lost events that are not recovered by client retries, or fraud-rule correctness depends on every event landing exactly once.

## References

- [../../README.md NFR2](../../README.md).
- [0003](0003-kafka-decouples-fraud-engine.md), [0005](0005-idempotency-key-header.md).
- [../business-rules/transfer-rules.md](../business-rules/transfer-rules.md) — "Commit-then-publish ordering" rule.
