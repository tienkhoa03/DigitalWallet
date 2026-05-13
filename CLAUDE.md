# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

DigitalWallet is a **greenfield** multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager. **No application code exists yet** — only the design contract in [project-info.md](project-info.md) and supporting documents (see [§15](project-info.md#15-reference-materials)). The PRD source is the FR/NFR summary captured directly into `project-info.md` on 2026-05-12. When the code and `project-info.md` conflict, `project-info.md` is authoritative until an ADR under [.claude/rules/adr/](.claude/rules/adr/) supersedes it (spec — not yet implemented).

## Tech Stack (Mandated by Spec)

- **Language:** Java 21 (LTS). Required for virtual-thread support on Kafka consumers and HTTP threads — do not substitute.
- **Framework:** Quarkus 3.x LTS. Container-first runtime mandated; do not substitute Spring Boot or plain JAX-RS containers.
- **API style:** REST via JAX-RS (RESTEasy Reactive). PFM advisor uses async request-reply over the same REST surface — see NFR8.
- **ORM:** Hibernate ORM with Panache. Must support `LockModeType.PESSIMISTIC_WRITE` for NFR1.
- **Migrations:** Flyway, versioned SQL only. No runtime schema mutation.
- **Validation:** Hibernate Validator (Bean Validation).
- **Build tool:** Maven (ADR #7). Do not substitute Gradle.
- **Messaging client:** SmallRye Reactive Messaging (Kafka extension).
- **Resilience:** SmallRye Fault Tolerance — required for NFR8 LLM circuit breaker.
- **Persistence (ledger):** PostgreSQL 16. Money columns `numeric(19,4)`; timestamps `timestamptz`. Materialized views permitted for the PFM read model (NFR6).
- **Cache / locks / idempotency:** Redis 7. Not a source of truth — state must be reconstructable from Postgres + Kafka.
- **Event backbone:** Kafka. Topics: `transaction-events`, `fraud-alerts`, `pfm-threshold-alerts`, `advisor-requests`/`advisor-responses` (advisor topic names TBD).
- **Frontend:** React 18.x + TypeScript 5.x **strict** + Tailwind 3.x + Redux Toolkit (incl. RTK Query) + React Hook Form + Zod + pnpm + native WebSocket API.
- **Testing (backend):** JUnit 5, Mockito, Testcontainers (Postgres + Kafka + Redis), JaCoCo. ≥80% service-layer line coverage (NFR4) enforced in CI.
- **Testing (frontend):** Vitest + React Testing Library, c8 coverage, Playwright for E2E smoke per epic.
- **Deployment:** Docker + Docker Compose (single-host local-first stack). No Kubernetes in MVP.
- **CI/CD:** GitHub Actions — build, tests (incl. Testcontainers), JaCoCo gate, frontend lint+test.

## Architecture

Modular monolith organised under `backend/` as feature modules (`account`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`, `shared`), with two parallel execution streams decoupled by Kafka: the synchronous money path commits the ledger on the request thread, and one or more asynchronous Kafka consumers (Fraud, PFM, Dashboard, AI Advisor) run on separate thread pools. Cross-system consistency between the two streams is achieved with the Transactional Outbox Pattern, not by writing to Kafka inside the request handler (NFR2, NFR5). The frontend is a single React app serving both end users and the admin dashboard, with realtime updates over WebSocket.

### Synchronous stream (money path)

- Entry: REST endpoints for account, wallet, deposit, withdraw, transfer, statement.
- Concurrency: outer Redis distributed lock keyed on `wallet_id` (fast-fail), inner DB `SELECT … FOR UPDATE` via JPA `PESSIMISTIC_WRITE` (NFR1).
- Persistence: `@Transactional` boundary on the service layer; ledger row + outbox row committed atomically.
- Idempotency: mutating endpoints require an `Idempotency-Key` header; replays return the original outcome (NFR3).
- Output: writes to the outbox table only. The HTTP thread never publishes to Kafka directly (NFR5).
- Rate limiting: Redis token bucket on `POST /transfers` (10/min/user) and `POST /advisor/*` (5/hour/user).

### Asynchronous stream (Kafka consumers)

- Outbox poller: Quarkus `@Scheduled` drains the outbox into `transaction-events` with at-least-once semantics (NFR2). Consumers must be idempotent.
- Fraud path: `transaction-events` → velocity (FR2.1) + volume (FR2.2) checks → `fraud-alerts` → WebSocket fan-out to admin dashboard (FR3.2).
- PFM path: `transaction-events` → budget updater (event-time via `transaction_timestamp`, NFR7) → Redis hot read-model + Postgres materialized view backup (NFR6) → threshold checker → `pfm-threshold-alerts` → WebSocket / push (FR5.x).
- Dashboard path: `transaction-events` aggregator → live daily count/volume metrics → WebSocket push (FR3.1).
- AI advisor path: month-end aggregator → anonymised LLM prompt (NFR8 circuit-breaker wrapped) → response topic → WebSocket reply to user (HTTP 202 on request).

## Non-Negotiable Invariants

Treat any change that weakens these as a regression.

- **Hybrid concurrency (NFR1):** every wallet mutation MUST acquire the Redis distributed lock on `wallet_id` (short TTL) before opening the DB transaction, and the DB transaction MUST hold a `PESSIMISTIC_WRITE` row lock on the ledger row. Redis fails fast; DB is authoritative.
- **ACID + Outbox (NFR2):** ledger writes and outbox writes commit in a single DB transaction; Kafka publishing is performed only by the scheduled outbox poller. Consumers must be idempotent.
- **Idempotency (NFR3):** all mutating transfer/deposit/withdraw endpoints require an `Idempotency-Key` header and MUST return the original outcome on replay.
- **Coverage floor (NFR4):** ≥80% line coverage on the service layer; CI fails below this threshold.
- **Latency isolation (NFR5):** fraud, PFM, dashboard, and advisor logic MUST run in Kafka-consumer threads. The HTTP handler only persists the ledger row and emits an outbox event.
- **CQRS for budgets (NFR6):** budget state is NEVER maintained by direct `UPDATE`s against ledger tables. Redis is the hot read-model; a Postgres materialized view is the durable backup and rebuild source for Redis.
- **Event-time correctness (NFR7):** PFM uses `transaction_timestamp` from the Kafka payload, not consumer wall-clock; late events must not corrupt accounting.
- **LLM isolation (NFR8):** outbound LLM calls are wrapped in a SmallRye circuit breaker; the advisor endpoint follows async request-reply (HTTP 202 + WebSocket result) and never blocks HTTP threads.

## Commands

No build tooling is committed yet. When introducing it, use **Maven** for the backend (per §4.1, ADR #7) and **pnpm** for the frontend (per §4.2, ADR #8). Add the standard lifecycle commands here once they exist:

- Backend build: `./mvnw clean install` (spec — not yet implemented)
- Backend dev mode: `./mvnw quarkus:dev` (spec — not yet implemented)
- Backend tests (unit + integration via Testcontainers): `./mvnw test` (spec — not yet implemented)
- Backend coverage: `./mvnw verify` (JaCoCo report under `target/site/jacoco/`) (spec — not yet implemented)
- Frontend install: `pnpm install` (spec — not yet implemented)
- Frontend dev: `pnpm dev` (spec — not yet implemented)
- Frontend lint: `pnpm lint` (spec — not yet implemented)
- Frontend tests: `pnpm test` (Vitest); `pnpm e2e` (Playwright) (spec — not yet implemented)
- Local stack: `docker compose -f deploy/docker-compose.yml up` (spec — not yet implemented)

Detailed coding rules will land under [.claude/rules/backend_coding.md](.claude/rules/backend_coding.md) and [.claude/rules/frontend_coding.md](.claude/rules/frontend_coding.md) in step 2.

## Domain glossary at a glance

Full glossary will live under [docs/domain-knowledge/](docs/domain-knowledge/) in step 2.

- **Account:** a user identity; owns one or more wallets.
- **Wallet:** a balance-bearing record owned by an account, scoped to a single currency.
- **Transfer:** two-leg atomic operation (debit sender, credit receiver); cross-currency adds an FX leg using a cached rate.
- **Transaction:** umbrella term for any wallet movement (deposit, withdraw, or one leg of a transfer).
- **Outbox:** DB table written in the same transaction as a money mutation; drained to Kafka by a scheduled poller.
- **Idempotency Key:** client-supplied UUID guaranteeing at-most-once side effects on a mutating endpoint.
- **Event time:** `transaction_timestamp` carried in the Kafka payload (NFR7), distinct from the consumer's processing time.

## Module layout (planned)

(spec — not yet implemented)

```
DigitalWallet/
├── backend/                       # Quarkus application
│   ├── account/                   # FR1.1
│   │   ├── api/  service/  persistence/
│   ├── wallet/                    # FR1.2, FR1.3, FR1.4
│   │   ├── api/  service/  persistence/  event/
│   ├── fraud/                     # FR2.1, FR2.2, FR2.3
│   │   ├── consumer/  service/  event/
│   ├── pfm/                       # FR4.x, FR5.x
│   │   ├── api/  service/  consumer/  persistence/
│   ├── advisor/                   # FR6.x — LLM integration
│   │   ├── api/  service/  client/
│   ├── dashboard/                 # FR3.x
│   │   ├── api/  ws/  consumer/
│   └── shared/                    # money, idempotency, outbox, security
├── frontend/                      # React app (user app + admin dashboard)
└── deploy/                        # docker-compose, init scripts, env templates
```

Feature-based + layered: group by feature module, keep `api/` / `service/` / `persistence/` (and `consumer/` / `event/` where applicable) inside each module. Cross-cutting concerns live in `shared/`.

## Gaps in project-info.md to address before step 2

- **§4.4 advisor topic names** — `advisor-requests` / `advisor-responses` marked TBD; need final topic names, partitioning, and retention policy before wiring the AI advisor path.
- **§7 / §10 ADR #2 LLM provider** — provider is unanswered (Claude / OpenAI / Gemini / local). Blocks any concrete advisor integration, retry budgets, and the prompt sanitisation contract referenced in §8.
- **§8 / §16 #15 LLM data retention & training opt-out** — provider-dependent; cannot finalise the anonymisation rule until ADR #2 is closed.
- **§15 design/wireframes and prior art** — both TBD; frontend module conventions (component boundaries, layout primitives) will need a placeholder until designs land.
- **§17.1 performance budget** — only suggested numbers (≤200 ms P95 on `/transfers`, ≤1 s WebSocket fan-out). No committed SLO; the architecture review in NFR5 needs a concrete latency target.
- **§17.2 observability** — metrics sink and tracing stack TBD. Without these, the audit-log + SOC 2 invariants in §8 only cover business events, not operational signals.
- **§17.3 accessibility floor** — WCAG target TBD; affects frontend coding rules in step 2.
