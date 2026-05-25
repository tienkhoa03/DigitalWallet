# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

DigitalWallet is a multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager. The Claude-ready baseline (Phase A–E of the bootstrap) is scaffolded: `backend/` (Quarkus 3.15.6 LTS, Java 21, 7 modules with `shared/` infrastructure and Flyway V1; ships its own `Dockerfile` + `docker-compose.yml` that owns Postgres 16 + Kafka KRaft + Redis 7 + the optional Quarkus container), `frontend/` (Vite 5 + React 18 + TS 5 strict + Tailwind 3 + Redux Toolkit + RTK Query; ships its own `Dockerfile` + `docker-compose.yml` that serves the build via nginx and reverse-proxies `/api` to the backend), and `.github/workflows/ci.yml` (parallel backend/frontend jobs with JaCoCo 80% gate). Feature code is not yet written — only the cross-cutting shared infrastructure. The design contract lives in [project-info.md](project-info.md) and supporting documents (see [§15](project-info.md#15-reference-materials)). The PRD source is the FR/NFR summary captured directly into `project-info.md` on 2026-05-12. When the code and `project-info.md` conflict, `project-info.md` is authoritative until an ADR under [docs/decisions/](docs/decisions/) supersedes it.

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

Modular monolith organised under `backend/` as feature modules (`user`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`, `shared`), with two parallel execution streams decoupled by Kafka: the synchronous money path commits the ledger on the request thread (after a bounded fraud pre-check at the edge, NFR9), and one or more asynchronous Kafka consumers (Fraud, PFM, Dashboard, AI Advisor) run on separate thread pools. Cross-system consistency between the two streams is achieved with the Transactional Outbox Pattern, not by writing to Kafka inside the request handler (NFR2, NFR5). The frontend is a single React app serving both end users and the admin dashboard, with realtime updates over WebSocket.

### Synchronous stream (money path)

- Entry: REST endpoints for signup, wallet, deposit, withdraw, transfer, statement.
- Fraud pre-check: bounded Redis sliding-window counter lookups (velocity FR2.1, volume FR2.2) plus a `user.fraud_status` read (FR2.4) before opening the DB transaction; a breach rejects the request inline with `fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended` (NFR9; the `account.suspended` error key is preserved for API back-compat). Blocked attempts write a `transaction.blocked` outbox event in a short `@Transactional` boundary. *(MVP: the `audit_log` row originally specified for block paths is deferred — see project-info.md §8.)*
- Concurrency: outer Redis distributed lock keyed on `wallet_id` (fast-fail), inner DB `SELECT … FOR UPDATE` via JPA `PESSIMISTIC_WRITE` (NFR1).
- Persistence: `@Transactional` boundary on the service layer; ledger row + outbox row committed atomically.
- Idempotency: mutating endpoints require an `Idempotency-Key` header; replays return the original outcome (NFR3). Implemented via the `idempotency_record` table with UNIQUE`(user_id, endpoint, idempotency_key)` and an `IN_FLIGHT`/`COMPLETED` status column.
- Output: writes to the outbox table only. The HTTP thread never publishes to Kafka directly (NFR5).
- Rate limiting: Redis token bucket on `POST /transfers` (10/min/user) and `POST /advisor/*` (5/hour/user).

### Asynchronous stream (Kafka consumers)

- Outbox poller: Quarkus `@Scheduled` drains the outbox into `transaction-events` with at-least-once semantics (NFR2). Consumers must be idempotent.
- Fraud path: `transaction-events` → asynchronous fraud engine (cross-event analysis, repeat-breach detection, suspension policy — flipping `user.fraud_status` to `SUSPENDED` per FR2.4) → `fraud-alerts` (FR2.5) → WebSocket fan-out to admin (FR3.2). Inline blocking lives in the sync pre-check; the async path owns alerting and (auto) suspension state changes. **Manual unsuspend is deferred for MVP** — see project-info.md §8.
- PFM path: `transaction-events` → budget updater (event-time via `event_timestamp`, NFR7) → Redis hot read-model + Postgres materialized view backup (NFR6) → threshold checker → `pfm-threshold-alerts` → WebSocket / push (FR5.x).
- Dashboard path: `transaction-events` aggregator → live daily count/volume metrics → WebSocket push (FR3.1).
- AI advisor path: month-end aggregator → anonymised LLM prompt (NFR8 circuit-breaker wrapped) → response topic → WebSocket reply to user (HTTP 202 on request).

## Non-Negotiable Invariants

Treat any change that weakens these as a regression.

- **Hybrid concurrency (NFR1):** every wallet mutation MUST acquire the Redis distributed lock on `wallet_id` (short TTL) before opening the DB transaction, and the DB transaction MUST hold a `PESSIMISTIC_WRITE` row lock on the ledger row. Redis fails fast; DB is authoritative.
- **ACID + Outbox (NFR2):** ledger writes and outbox writes commit in a single DB transaction; Kafka publishing is performed only by the scheduled outbox poller. Consumers must be idempotent.
- **Idempotency (NFR3):** all mutating transfer/deposit/withdraw endpoints require an `Idempotency-Key` header and MUST return the original outcome on replay.
- **Coverage floor (NFR4):** ≥80% line coverage on the service layer; CI fails below this threshold.
- **Latency isolation (NFR5):** the HTTP path MAY perform fast, bounded Redis-counter pre-checks (fraud velocity / volume per FR2.1–FR2.2, plus `user.fraud_status` lookup per FR2.4), but MUST NOT run heavy fraud / PFM / dashboard analytics inline. Cross-event fraud analysis, alert fan-out, suspension policy, PFM aggregation, and dashboard aggregation MUST run in Kafka-consumer threads.
- **CQRS for budgets (NFR6):** budget state is NEVER maintained by direct `UPDATE`s against ledger tables. Redis is the hot read-model; a Postgres materialized view is the durable backup and rebuild source for Redis.
- **Event-time correctness (NFR7):** PFM uses `event_timestamp` from the Kafka payload, not consumer wall-clock; late events must not corrupt accounting.
- **LLM isolation (NFR8):** outbound LLM calls are wrapped in a SmallRye circuit breaker; the advisor endpoint follows async request-reply (HTTP 202 + WebSocket result) and never blocks HTTP threads.
- **Synchronous fraud blocking (NFR9):** every wallet mutation MUST evaluate velocity (FR2.1), volume (FR2.2), and `user.fraud_status` (FR2.4) on the request thread before opening the DB transaction. Counters live in Redis (sliding window); suspension state lives in Postgres. Async fraud analytics, alert fan-out (FR2.5), and the suspension-policy decision (when to flip a user to `SUSPENDED`) remain on the Kafka consumer.

## Commands

Backend uses **Maven** (per §4.1, ADR #7); frontend uses **pnpm** (per §4.2, ADR #8). Run commands from the relevant subdirectory unless otherwise noted:

- Backend build: `cd backend && ./mvnw clean install`
- Backend dev mode: `cd backend && ./mvnw quarkus:dev`
- Backend tests (unit + integration via Testcontainers): `cd backend && ./mvnw test`
- Backend coverage: `cd backend && ./mvnw verify` (JaCoCo report under `backend/target/site/jacoco/`; 80% gate on `com/digitalwallet/*/service/**`)
- Frontend install: `cd frontend && pnpm install`
- Frontend dev: `cd frontend && pnpm dev`
- Frontend lint: `cd frontend && pnpm lint`
- Frontend tests: `cd frontend && pnpm test` (Vitest); `cd frontend && pnpm e2e` (Playwright)
- Local infra (Postgres + Kafka + Redis): `docker compose -f backend/docker-compose.yml up -d`
- Local stack incl. containerised backend: `docker compose -f backend/docker-compose.yml --profile app up -d`
- Local frontend (nginx + dist, joins `dw-net` external network — start backend first): `docker compose -f frontend/docker-compose.yml up -d`

Detailed coding rules will land under [.claude/rules/backend_coding.md](.claude/rules/backend_coding.md) and [.claude/rules/frontend_coding.md](.claude/rules/frontend_coding.md) in step 2.

## Domain glossary at a glance

Full glossary will live under [docs/domain-knowledge/](docs/domain-knowledge/) in step 2.

- **User:** a signed-up identity stored in the `user` table; owns wallets, budgets, and fraud alerts. Has an immutable `base_currency` chosen at signup. (Spec earlier called this "Account"; renamed in the schema. The API error key `account.suspended` is preserved for back-compat.)
- **Wallet:** a balance-bearing record owned by a user, scoped to a single currency. A user MAY own multiple wallets in the same currency (disambiguated by `label`).
- **Transfer:** two-leg atomic operation (debit sender, credit receiver). Produces 2 `transaction` rows sharing a `transfer_id`. Cross-currency snapshots the FX rate on both legs.
- **Transaction:** one leg of a wallet movement — one row in the `transaction` table. Deposit/withdraw = 1 row; transfer = 2 rows.
- **Outbox:** DB table written in the same transaction as a money mutation; drained to Kafka by a scheduled poller. `published_at IS NULL` marks the poll queue.
- **Idempotency Key:** client-supplied UUID guaranteeing at-most-once side effects on a mutating endpoint. Tracked in the `idempotency_record` table.
- **Event time:** `event_timestamp` carried in the Kafka payload (NFR7), distinct from the consumer's processing time and the `created_at` DB insert column.
- **Fraud counter / Fraud status / Fraud block:** Redis sliding-window counter and `user.fraud_status` enum used by the sync pre-check (NFR9) to reject suspicious transactions inline; full definitions in [project-info.md §9](project-info.md#9-domain-glossary).

## Module layout

Module skeleton scaffolded under `backend/src/main/java/com/digitalwallet/` (each module is currently a `package-info.java` placeholder plus, for `shared/`, the cross-cutting infrastructure). Feature code lands per-vertical-slice.

```
DigitalWallet/
├── backend/                       # Quarkus application + its deploy tier
│   ├── Dockerfile                 # multi-stage JVM (eclipse-temurin:21-jre)
│   ├── docker-compose.yml         # Postgres 16 + Kafka KRaft + Redis 7 + (--profile app) backend
│   ├── env.template               # backend + infra env (DB / Kafka / Redis / JWT / LLM / fraud)
│   ├── postgres/init/             # Postgres init scripts (bootstraps test DB)
│   ├── user/                      # FR1.1 (signup, role, base_currency, fraud_status)
│   │   ├── api/  service/  persistence/
│   ├── wallet/                    # FR1.2, FR1.3, FR1.4
│   │   ├── api/  service/  persistence/  event/
│   ├── fraud/                     # FR2.1, FR2.2, FR2.3, FR2.4, FR2.5
│   │   ├── consumer/  service/  event/
│   ├── pfm/                       # FR4.x, FR5.x
│   │   ├── api/  service/  consumer/  persistence/
│   ├── advisor/                   # FR6.x — LLM integration
│   │   ├── api/  service/  client/
│   ├── dashboard/                 # FR3.x
│   │   ├── api/  ws/  consumer/
│   └── shared/                    # money, idempotency, outbox, security
└── frontend/                      # React app (user app + admin dashboard) + its deploy tier
    ├── Dockerfile                 # multi-stage Node 20 build → nginx 1.27
    ├── docker-compose.yml         # nginx serving dist/, joins backend's dw-net
    ├── nginx.conf                 # static + /api reverse-proxy + WebSocket upgrade
    └── env.template               # FRONTEND_HOST_PORT, VITE_API_BASE_URL (no secrets — VITE_* is public)
```

Feature-based + layered: group by feature module, keep `api/` / `service/` / `persistence/` (and `consumer/` / `event/` where applicable) inside each module. Cross-cutting concerns live in `shared/`.

## Gaps in project-info.md to address before step 2

- **§4.4 advisor topic names** — `advisor-requests` / `advisor-responses` marked TBD; need final topic names, partitioning, and retention policy before wiring the AI advisor path.
- **§7 / §10 ADR #2 LLM provider** — provider is unanswered (Claude / OpenAI / Gemini / local). Blocks any concrete advisor integration, retry budgets, and the prompt sanitisation contract referenced in §8.
- **§8 / §16 #15 LLM data retention & training opt-out** — provider-dependent; cannot finalise the anonymisation rule until ADR #2 is closed.
- **§15 design/wireframes and prior art** — both TBD; frontend module conventions (component boundaries, layout primitives) will need a placeholder until designs land.
- **§17.1 performance budget** — MVP dev-target budget committed (`/transfers` ≤ 200 ms P95 incl. fraud pre-check; async suspension propagation ≤ 1 s; WebSocket alert fan-out ≤ 1 s) on a single-node docker-compose stack; not a production SLO. Revisit once observability (§17.2) lands and we have real measurements.
- **§17.2 observability** — metrics sink and tracing stack TBD. Without these, the audit-log + SOC 2 invariants in §8 only cover business events, not operational signals.
- **§17.3 accessibility floor** — WCAG target TBD; affects frontend coding rules in step 2.
