# Business rules

This page is the index for the project's business-rule pages and the matrix that maps every non-functional invariant onto the layer responsible for enforcing it.

**Business rules vs. coding rules.** Business rules describe the **product invariants** the system must uphold — for example, that money cannot be created or destroyed, that a transfer requires an `Idempotency-Key`, or that PFM accounting uses event time and not wall-clock time. Coding rules describe **how** code in this repository must be written — naming, layering, lint configuration, prompt scaffolds — and live under [../../.claude/rules/](../../.claude/rules/) `(not yet present — generated in step 3)`. Tests check both, but a rule-page failure usually surfaces as a product bug; a coding-rule failure usually surfaces as a CI lint or formatting failure.

Source documents: [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs) (functional requirements), [../../project-info.md §6](../../project-info.md#6-non-functional-requirements--invariants) (non-functional invariants), [../../project-info.md §8](../../project-info.md#8-security-baseline) (security baseline), [../../CLAUDE.md](../../CLAUDE.md) (Non-Negotiable Invariants).

## NFR enforcement matrix

| NFR | Rule one-liner | Enforcement layer | Source |
|---|---|---|---|
| NFR1 | Wallet mutations acquire a Redis distributed lock keyed on `wallet_id` (short TTL, fail-fast) before opening the DB transaction, which then takes a `LockModeType.PESSIMISTIC_WRITE` row lock; Redis fails fast, DB is authoritative. | Application service (use case) + Redis lock helper (`shared/`) + outbound persistence adapter. | [../../project-info.md §6 NFR1](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR2 | The ledger row and the outbox row commit in the same `@Transactional` boundary; a Quarkus `@Scheduled` poller drains the outbox into Kafka with at-least-once delivery. Consumers must be idempotent. | Application service (use case) + scheduled outbox poller (`shared/`). | [../../project-info.md §6 NFR2](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR3 | Every mutating transfer/deposit/withdraw endpoint requires an `Idempotency-Key` HTTP header; replays return the original outcome. | Idempotency middleware (`shared/`). | [../../project-info.md §6 NFR3](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR4 | Application-service-layer line coverage stays ≥80% under JUnit 5 + Mockito; CI fails below the threshold. | JaCoCo gate in GitHub Actions. | [../../project-info.md §6 NFR4](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR5 | The HTTP path MAY perform fast, bounded Redis-counter pre-checks (fraud velocity / volume per FR2.1–FR2.2, plus `account.fraud_status` lookup per FR2.4) but MUST NOT run heavy fraud / PFM / dashboard analytics inline. Cross-event fraud analysis, alert fan-out, suspension policy, PFM aggregation, and dashboard aggregation run in Kafka-consumer threads on separate pools. | Architecture review + module boundaries. | [../../project-info.md §6 NFR5](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR6 | Budget state is never maintained by direct `UPDATE` on ledger tables. Redis hashes are the hot read-model (updated by the PFM inbound messaging adapter); a Postgres materialized view is the durable backup and rebuild source for Redis. | PFM application service (use case) + PFM inbound messaging adapter + scheduled MV refresh + Redis rebuild job. | [../../project-info.md §6 NFR6](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR7 | PFM calculations use `event_timestamp` from the Kafka payload (event time), not consumer wall-clock; late events must not corrupt accounting. | PFM inbound messaging adapter logic with watermarks / reconciliation job. | [../../project-info.md §6 NFR7](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR8 | Outbound LLM calls are wrapped in a SmallRye circuit breaker; the advisor endpoint is asynchronous request-reply (HTTP 202 + WebSocket result) and never blocks HTTP threads. | SmallRye Fault Tolerance + WebSocket reply channel (`advisor/`). | [../../project-info.md §6 NFR8](../../project-info.md#6-non-functional-requirements--invariants) |
| NFR9 | The sync money path evaluates velocity (FR2.1), volume (FR2.2), and `account.fraud_status` (FR2.4) before opening the DB transaction. Counters live in Redis (sliding window); suspension state lives in Postgres. Async fraud analytics, alert fan-out (FR2.5), and the suspension-policy decision remain on the Kafka consumer. | Application-service (use case) fraud pre-check (`wallet/`) + Redis counters + Postgres `account.fraud_status` + fraud inbound messaging adapter for policy/alerting. | [../../project-info.md §6 NFR9](../../project-info.md#6-non-functional-requirements--invariants) |

## Cross-cutting security rules

Drawn from [../../project-info.md §8](../../project-info.md#8-security-baseline). These apply across every epic.

- **RBAC at the application service (use case).** Authorization is enforced inside the application service (use case), not only by JAX-RS annotations on the inbound web adapter. Two roles in MVP: `USER`, `ADMIN`, stored on `account.role`. `FRAUD_ANALYST` and multi-role-per-account are deferred — see ADR #9.
- **Audit log — DEFERRED in MVP.** The immutable `audit_log` table is not yet in scope ([../../project-info.md §8](../../project-info.md#8-security-baseline)). When manual unsuspend, role grants UI, or admin PII reads ship, the table returns together via an ADR superseding §8. Until then, MVP commits to RBAC at the application service (use case) + no PII in logs as the SOC 2 directional floor.
- **No PII in logs.** SLF4J logging at `INFO`/`WARN`/`ERROR`/`DEBUG` levels, but PII is never emitted.
- **LLM payload sanitisation.** Prompts to the LLM contain only aggregated amounts and category labels — no user identifiers.
- **Rate limits** via Redis token bucket: `POST /transfers` 10/min/user, `POST /advisor/*` 5/hour/user.
- **Secrets** via 12-factor env vars; gitleaks runs as a pre-commit hook.

## Per-epic rule pages

| Epic | Page |
|---|---|
| Epic 1 — Core Wallet Management | [core-wallet-management-rules.md](core-wallet-management-rules.md) |
| Epic 2 — Fraud Detection Engine | [fraud-detection-engine-rules.md](fraud-detection-engine-rules.md) |
| Epic 3 — Real-time Admin Dashboard | [real-time-admin-dashboard-rules.md](real-time-admin-dashboard-rules.md) |
| Epic 4 — AI-Driven Personal Finance Management | [ai-driven-personal-finance-management-rules.md](ai-driven-personal-finance-management-rules.md) |
| Epic 5 — PFM Notifications | [pfm-notifications-rules.md](pfm-notifications-rules.md) |
| Epic 6 — AI Advisor | [ai-advisor-rules.md](ai-advisor-rules.md) |
