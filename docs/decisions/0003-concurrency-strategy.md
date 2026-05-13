# 0003 — Concurrency strategy for wallet mutations

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

The ledger must not race under concurrent retries, spam clicks, or genuine parallel transfers (NFR1, [../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants)). The team needs an ADR capturing why a hybrid lock strategy is preferred over a single-lock approach. Source: [../../project-info.md §10 row 3](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Hybrid: Redis distributed lock (outer) + DB `SELECT … FOR UPDATE` (inner)** — outer lock fences out retries cheaply; inner DB lock is authoritative.
- **DB `SELECT … FOR UPDATE` only** — simpler but allows hot-row pile-up under retries.
- **Redis distributed lock only** — fails fast but Redis is not authoritative and cannot guarantee ACID against the ledger.
- **Optimistic locking with a version column** — lower contention but unpredictable retry storms under spam clicks.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
