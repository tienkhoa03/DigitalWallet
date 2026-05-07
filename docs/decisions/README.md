# Architecture Decisions (ADRs)

A decision is worth an ADR when at least one of these holds:

- It is hard to reverse (DB engine, auth model, framework family, public API contract).
- The reasoning is non-obvious from the code alone.
- It constrains future code (e.g., a forced version freeze).
- Reasonable engineers could pick differently.

It is **not** worth an ADR when:

- The choice is routine and reversible (lib version bump, file location).
- It is a coding-style rule (those go in `.claude/rules/`).
- It is a time-bound ops note (use a runbook or a PR description).

## Format

1. Copy [template.md](template.md) to `NNNN-<slug>.md` with the next number.
2. Fill in `Context`, `Decision`, `Options`, `Consequences` honestly. Do not advocate for the chosen option in the Context.
3. Status starts as `Proposed`. Move to `Accepted` on merge. Use `Superseded by NNNN` rather than editing accepted ADRs in place.

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-quarkus-over-spring-boot.md) | Quarkus over Spring Boot and plain Jakarta EE | Accepted |
| [0002](0002-postgresql-with-flyway.md) | PostgreSQL with Flyway for balance storage | Accepted |
| [0003](0003-kafka-decouples-fraud-engine.md) | Kafka decouples the fraud engine from the transaction path | Accepted |
| [0004](0004-pessimistic-locking-balance-updates.md) | Pessimistic locking for balance updates | Accepted |
| [0005](0005-idempotency-key-header.md) | `Idempotency-Key` header on transfer endpoints | Accepted |
| [0006](0006-outbox-or-publish-after-commit.md) | DB-Kafka consistency: Outbox or commit-then-publish | Proposed |
| [0007](0007-two-stream-architecture.md) | Two-stream architecture (sync core banking, async fraud) | Accepted |
