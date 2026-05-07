# 0004 — Pessimistic locking for balance updates

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

[NFR1](../../README.md) requires strict prevention of race conditions on balance updates and explicitly names `SELECT … FOR UPDATE` (via JPA `LockModeType`) as the baseline approach, with Redisson distributed locks as an advanced alternative.

## Decision

We will use JPA `LockModeType.PESSIMISTIC_WRITE` (translating to `SELECT … FOR UPDATE` in PostgreSQL) on every wallet balance read that is followed by a write. Locks are acquired in a deterministic order (e.g., by wallet ID ascending) to prevent cross-transfer deadlocks. Distributed locks via Redisson remain an opt-in upgrade for a future cross-instance scenario but are not required for the MVP.

## Options considered

### Option A — Optimistic locking (`@Version`)
- Pros: no row-level wait; high throughput under low contention.
- Cons: requires retry logic on the client; hot wallets retry constantly; the spec explicitly steers away.

### Option B — Single-threaded actor per wallet
- Pros: serializes naturally; no lock manager needed.
- Cons: requires sticky routing; foreign to a Quarkus / CDI-based app; not what the spec describes.

### Option C — Pessimistic JPA locking  *(chosen)*
- Pros: matches NFR1 verbatim; correct under concurrent load; minimal extra code.
- Cons: row-level waits under hot contention; deadlock risk if lock order is inconsistent (mitigated by ID ordering).

### Option D — Redisson distributed lock
- Pros: works across multiple JVMs.
- Cons: extra dependency; correctness depends on Redis availability; overkill for the MVP.

## Consequences

- Easier: reasoning about correctness; one transfer at a time per wallet pair.
- Harder: throughput on a single hot wallet; deadlock prevention requires discipline.
- Live with: lock-acquisition latency under contention.
- Revisit if: we scale to multiple backend instances and a single Postgres lock domain becomes a bottleneck — at which point Option D returns.

## References

- [../../README.md NFR1](../../README.md).
- [../business-rules/transfer-rules.md](../business-rules/transfer-rules.md).
