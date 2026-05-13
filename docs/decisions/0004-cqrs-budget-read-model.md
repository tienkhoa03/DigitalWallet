# 0004 — CQRS read-model for budgets

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

NFR6 ([../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants)) forbids maintaining budget state by direct `UPDATE` against ledger tables. The PFM consumer must keep an up-to-date view of bucket spending for every active user without contending on the money path. The team needs an ADR fixing where that read model lives, how it is rebuilt after data loss, and how it is kept consistent with the ledger. Source: [../../project-info.md §10 row 4](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Both: Redis (hot path) + Postgres materialized view (durable backup, rebuild source)** — fastest read path with a recoverable backup.
- **Redis only** — fastest path but no rebuild source if Redis is flushed.
- **Postgres materialized view only** — durable but slower; refresh cost rises with user count.
- **Direct table updates with row locks** — explicitly forbidden by NFR6.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
