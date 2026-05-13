# 0005 — Outbox publisher

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

NFR2 requires that the ledger row and the Kafka event commit atomically with respect to each other — money cannot be created or destroyed, and events cannot be lost or duplicated relative to DB state ([../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants)). The HTTP path must not publish to Kafka directly (NFR5). The team needs an ADR fixing the publishing mechanism (CDC log-tail vs. application-driven poller). Source: [../../project-info.md §10 row 5](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Transactional outbox + Quarkus `@Scheduled` poller (no Debezium)** — keeps the runtime self-contained; one process, one deployment artifact.
- **Debezium CDC on Postgres WAL** — battle-tested but adds a sidecar service and complicates the local-first compose stack.
- **Commit-then-publish on the request thread** — explicitly forbidden by NFR2/NFR5 (the HTTP thread would block on Kafka and a crash between commit and publish would lose events).

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
