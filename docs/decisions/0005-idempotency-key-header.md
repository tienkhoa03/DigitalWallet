# 0005 — `Idempotency-Key` header on transfer endpoints

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

[NFR3](../../README.md) requires transfer APIs to honour an `Idempotency-Key` HTTP header so that a retried (or spam-clicked) request results in at most one balance change.

## Decision

We will require an `Idempotency-Key` (UUID) header on every state-changing transfer endpoint. Keys are stored in Redis with a TTL (proposed default: 24 hours — verify when chosen) along with a hash of the request payload and the original response. Replays with the same key + payload return the cached response; replays with the same key + a different payload are rejected with `409 Conflict`.

## Options considered

### Option A — No idempotency, rely on the client
- Pros: zero infrastructure.
- Cons: violates NFR3; a single network blip double-debits.

### Option B — Idempotency table in Postgres
- Pros: durable record; survives Redis outage.
- Cons: writes a row per request on the hot path; Redis is faster and the data is naturally short-lived.

### Option C — Redis with TTL  *(chosen)*
- Pros: fast lookup; TTL-based cleanup; the spec already lists Redis for idempotency in [§2](../../README.md).
- Cons: lost on Redis flush — acceptable because the window is short and the alternative (a DB row) costs every request.

## Consequences

- Easier: clients can retry safely.
- Harder: Redis becomes load-bearing on the request path.
- Live with: a small extra latency for the lookup; coordinated TTL choice.
- Revisit if: Redis availability proves insufficient — fall back to Option B.

## References

- [../../README.md NFR3, §2, §5](../../README.md).
- [../business-rules/transfer-rules.md](../business-rules/transfer-rules.md).
