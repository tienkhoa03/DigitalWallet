# 0002 — PostgreSQL with Flyway for balance storage

- **Status**: Accepted
- **Date**: 2026-05-07
- **Deciders**: project author

## Context

The system must hold money balances under ACID guarantees ([NFR2](../../README.md)) and use pessimistic row-level locking ([NFR1](../../README.md)). The spec ([../../README.md §2](../../README.md)) names PostgreSQL as the master data store and Flyway as the schema-migration tool.

## Decision

We will use PostgreSQL as the single source of truth for accounts, wallets, and transaction history. Schema evolution is managed exclusively through forward-only Flyway migrations.

## Options considered

### Option A — MySQL / MariaDB
- Pros: also supports `SELECT … FOR UPDATE`; widely deployed.
- Cons: weaker default isolation guarantees in some configurations; no clear advantage over Postgres for this workload.

### Option B — A NoSQL store (MongoDB, DynamoDB)
- Pros: easy to scale horizontally.
- Cons: weaker transactional guarantees across rows; financial-ledger semantics fight the data model.

### Option C — PostgreSQL + Flyway  *(chosen)*
- Pros: strong default isolation, rich pessimistic-lock support, mature JPA dialect, Flyway is the de-facto standard for forward-only migrations on the JVM.
- Cons: vertical-scaling ceiling — irrelevant at the demo scale this project targets.

## Consequences

- Easier: writing a correct ledger; reasoning about concurrency.
- Harder: any future migration to a different engine — Postgres-isms (`numeric`, `timestamptz`, `jsonb`) will leak.
- Live with: a single SQL dialect across the codebase.
- Revisit if: the project ever needs cross-region active-active writes.

## References

- [../../README.md §2](../../README.md), [NFR1](../../README.md), [NFR2](../../README.md).
- [../database/README.md](../database/README.md), [../database/migrations.md](../database/migrations.md).
