# Project Info — Digital Wallet

> Filled from the project description provided 2026-05-12. Items marked `❓ TBD` are open and tracked in §16.

---

## §1 Project Identity

- **Project name:** `DigitalWallet`
- **One-line description:** A multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager (PFM) that learns from each user's spending stream.
- **Primary value:** Prove that a single event-driven backbone can serve both a strict ACID money ledger AND a derived, AI-augmented analytics stream without coupling them on the request thread.
- **Status:** greenfield
- **Repository:** GitHub (URL: https://github.com/tienkhoa03/DigitalWallet)
- **License:** Proprietary

---

## §2 Stakeholders & users

### 2.1 User personas

| Persona | Goals | Frequency |
|---|---|---|
| End user (wallet holder) | Deposit/withdraw, P2P transfer, view statement, set budgets, receive PFM advice | daily |
| Admin / Ops | Monitor live transaction volume, react to fraud alerts in real time | daily (during business hours) |

> **MVP scope cut.** The "Fraud analyst" persona is deferred — no manual unsuspend / fraud-rule tuning UI ships in MVP. Admin temporarily covers the read side of fraud alerts.

### 2.2 Roles in the system

| Role | Permissions | Notes |
|---|---|---|
| `USER` | own wallet CRUD, transfer, statement, budgets, PFM advice | default role on signup, stored as a column on `user.role` |
| `ADMIN` | live metrics dashboard, system-wide read, fraud alert stream | needed for FR3.1, FR3.2 (admin covers analyst reads in MVP) |

> **MVP scope cut.** `FRAUD_ANALYST` is deferred together with the analyst workflow. Multi-role-per-user (`role_assignment` table) is also deferred — each user holds exactly one role on the `user` row. When manual unsuspend, fraud-rule tuning, or multi-role grants ship, reintroduce both via an ADR superseding [docs/decisions/0009-rbac-roles.md](docs/decisions/0009-rbac-roles.md). See [docs/database/README.md](docs/database/README.md) MVP scope notes.

---

## §3 Architecture style

- **High-level shape:** Modular monolith with **two-stream architecture** — a synchronous transactional core and one or more asynchronous Kafka consumers (Fraud, PFM, Dashboard).
- **Synchronous vs. asynchronous boundaries:**
  - Sync: user/wallet CRUD, deposit/withdraw, P2P transfer (the request thread MUST commit the ledger row and return).
  - Async (Kafka consumers, separate thread pools):
    - Fraud engine (velocity + volume checks, alert publishing).
    - PFM budget updater (event-time aware, CQRS read model in Redis / materialized view).
    - Admin dashboard live-metrics aggregator (pushed to clients via WebSocket).
    - LLM-backed PFM advisor (async request-reply: HTTP 202 → result via WebSocket).
- **Major streams / paths:**
  1. **Money path** — REST → idempotency check → rate limit → **synchronous fraud pre-check** (Redis sliding-window counters for velocity + volume, plus `user.fraud_status` lookup) → service (Redis wallet lock + ACID DB transaction with `PESSIMISTIC_WRITE`) → ledger row + outbox row committed atomically → return. A breach of velocity / volume rejects the transaction inline (HTTP 422 `fraud.velocity_exceeded` / `fraud.volume_exceeded`); a suspended user is rejected with `account.suspended` (error key kept for backward-compat with the API contract). Blocked attempts emit a `transaction.blocked` event via the outbox so the async stream retains a full record. *(MVP: `audit_log` is deferred — see §8.)*
  2. **Fraud path** — Kafka `transaction-events` → asynchronous fraud engine (cross-event analysis, repeat-breach detection, **user-suspension policy** — flipping `user.fraud_status` to `SUSPENDED`) → Kafka `fraud-alerts` → WebSocket fan-out to admin. The async path owns alerting and suspension state changes; immediate per-transaction blocking lives in the sync pre-check.
  3. **PFM path** — Kafka `transaction-events` → budget updater (event-time) → Redis/MV → threshold checker → WebSocket / Push.
  4. **AI advisor path** — month-end aggregator → LLM (circuit-breaker wrapped) → result topic → WebSocket reply to user.
- **Deployment topology:** docker-compose stack (app, Postgres, Kafka, Redis, frontend) for local-first dev. No K8s targeted in MVP.
- **Real-time channel:** WebSocket (FR3.2, FR5.1, NFR8 async reply).

### 3.1 Module / package organization

```
DigitalWallet/
├── backend/                       # Quarkus application + its deploy tier
│   ├── Dockerfile                 # multi-stage JVM (eclipse-temurin:21-jre)
│   ├── docker-compose.yml         # Postgres 16 + Kafka KRaft + Redis 7 + (--profile app) backend
│   ├── env.template               # backend + infra env (DB / Kafka / Redis / JWT / LLM / fraud)
│   ├── postgres/init/             # init scripts (test DB bootstrap)
│   ├── user/                      # FR1.1 (signup, role, base_currency, fraud_status)
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
└── frontend/                      # React app (user app + admin dashboard) + its deploy tier
    ├── Dockerfile                 # multi-stage Node 20 build → nginx 1.27
    ├── docker-compose.yml         # nginx serving dist/, joins backend's dw-net
    ├── nginx.conf                 # static + /api reverse-proxy + WebSocket upgrade
    └── env.template               # public-only config (no secrets — VITE_* is readable in the browser)
```

**Organising principle:** Feature-based + layered. Group code by feature module under `backend/`, separate the standard layers (`api/`, `service/`, `persistence/`, plus `consumer/` and `event/` where applicable) inside each module. The `shared/` module holds cross-cutting concerns (money type, idempotency middleware, outbox poller, security).

---

## §4 Tech stack (mandated)

### 4.1 Backend

| Concern | Choice | Version target | Reason / constraint |
|---|---|---|---|
| Language | Java | 21 (LTS) | LTS, virtual-thread support for Kafka consumers and HTTP threads |
| Framework | Quarkus | 3.x LTS | Container-first, fast boot, first-class Kafka & Hibernate Reactive/ORM Panache |
| API style | REST | — | Maps cleanly to the synchronous money path; PFM advisor uses async request-reply over the same REST surface |
| API library | JAX-RS (RESTEasy Reactive) | — | Quarkus default |
| ORM / persistence | Hibernate ORM with Panache | — | Idiomatic Quarkus, supports pessimistic locking via JPA `LockModeType.PESSIMISTIC_WRITE` (NFR1) |
| Migrations | Flyway | — | Versioned SQL migrations, fits ACID ledger schema |
| Validation | Bean Validation (Hibernate Validator) | — | Quarkus default |
| Build tool | Maven | — | Quarkus first-class, widest archetype/example coverage |
| Messaging client | SmallRye Reactive Messaging (Kafka) | — | Quarkus Kafka extension |
| Resilience | SmallRye Fault Tolerance | — | Circuit breaker for LLM calls (NFR8) |

### 4.2 Frontend

| Concern | Choice | Version target | Reason / constraint |
|---|---|---|---|
| Framework | React | 18.x | Wide ecosystem; suits live dashboard + user app |
| Language | TypeScript | 5.x strict | Type-safety required given API surface size |
| Styling | Tailwind CSS | 3.x | Utility-first, fast iteration on the admin dashboard |
| State mgmt | Redux Toolkit (incl. RTK Query) | latest | Battle-tested; RTK Query handles REST caching + WebSocket subscriptions consistently |
| Forms | React Hook Form + Zod | latest | Performant uncontrolled forms with type-safe schema validation |
| Realtime client | Native WebSocket API | — | Required for FR3.2 alert toasts, FR5.1 budget alerts, and the async LLM reply (NFR8) |
| Package mgr | pnpm | latest | Faster installs, content-addressable store |

### 4.3 Persistence & data

| Store | Purpose | Constraint |
|---|---|---|
| PostgreSQL 16 | Authoritative ledger (users, wallets, transactions, outbox, budgets) | ACID, `numeric(19,4)` for money, `timestamptz` for timestamps; materialized views allowed for PFM read model (NFR6) |
| Redis 7 | Cache, distributed locks, idempotency-key store, CQRS read model for budgets (NFR6) | Not the source of truth — must be reconstructable from Postgres + Kafka |
| Kafka | Event backbone (see §4.4) | Not a persistence layer per se, but retains `transaction-events` long enough for replay |

### 4.4 Messaging & integration

| Tech | Topics / queues | Purpose |
|---|---|---|
| Kafka | `transaction-events` | Every successful wallet movement is published here (FR2.3). Consumed by fraud, PFM, and dashboard streams. |
| Kafka | `fraud-alerts` | Output of the fraud engine. Consumed by the dashboard WebSocket fan-out (FR3.2). |
| Kafka | `pfm-threshold-alerts` | Output of the budget threshold checker (FR5.1). Consumed by the user-notification WebSocket fan-out. |
| Kafka | `advisor-requests` / `advisor-responses` ❓ TBD | Async request-reply for the LLM advisor (NFR8). |

### 4.5 Testing & quality

| Layer | Tool | Floor |
|---|---|---|
| Unit (backend) | JUnit 5 + Mockito | ≥80% service-layer line coverage (NFR4) |
| Integration | Testcontainers (Postgres + Kafka + Redis) | Every public repository method + every Kafka consumer |
| Unit (frontend) | Vitest + React Testing Library | Every reducer / selector / hook with branching logic |
| E2E | Playwright | Smoke per epic (signup → transfer → see fraud alert; create budget → spend → see threshold alert) |
| Coverage tool | JaCoCo (backend); c8 via Vitest (frontend) | — |

### 4.6 Deployment

- **Container runtime:** Docker
- **Orchestration:** Docker Compose (single-host stack for dev and demo)
- **Cloud target:** N/A — local-first MVP
- **CI/CD:** GitHub Actions (build, test, JaCoCo coverage gate, frontend lint+test)

---

## §5 Functional requirements (Epics & FRs)

### Epic 1: Core Wallet Management

- **FR1.1:** Users sign up (creating a `user` record with a chosen `base_currency`) and open one or more internal wallets (multi-currency: a user MAY hold multiple wallets in the same currency — e.g. separate "Savings USD" and "Travel USD" wallets — in addition to wallets in different currencies).
- **FR1.2:** Deposit / withdraw endpoints simulate top-up and cash-out by updating the wallet balance through the authoritative ledger.
- **FR1.3:** P2P transfer — internal money movement addressed by the recipient's `user_id` (no `account_number` in MVP — single identifier per user). Payload accepts an optional `category_id` (e.g. Entertainment, Food) to power PFM analytics. Cross-currency transfers are converted at transfer time using a cached FX rate (see ADR #6 and §9); the snapshotted `exchange_rate` is stored on both legs of the resulting `transaction` rows.
- **FR1.4:** Transaction history — statement endpoint with filters by time range and transaction type.

### Epic 2: Fraud Detection Engine

> Enforcement model: the fraud engine is **preventive**, not purely observational. Velocity (FR2.1) and volume (FR2.2) checks reject the offending transaction inline on the synchronous money path; the async fraud consumer (FR2.4, FR2.5) owns deeper analysis, alerting, and user-level suspension policy.

- **FR2.1:** Velocity check — the sync money path MUST reject any deposit / withdraw / transfer that would push the user past 5 transactions within a 1-minute sliding window. Rejection returns HTTP 422 `fraud.velocity_exceeded`. Counters are maintained in Redis (per user, per rule); the pre-check runs before the wallet lock is acquired.
- **FR2.2:** Volume check — the sync money path MUST reject any transaction that would push the user's cumulative volume above a configurable threshold (default > $50,000 within 1 hour). Rejection returns HTTP 422 `fraud.volume_exceeded`. Same Redis-counter model as FR2.1.
- **FR2.3:** Event publishing — every **successful** transaction MUST publish an event to Kafka `transaction-events`; every **blocked** transaction MUST also publish a `transaction.blocked` outbox event so the async stream retains a full record of denied attempts alongside committed ones. The block path opens its own short `@Transactional` boundary (no ledger row, no wallet lock) so the outbox row commits atomically — preserving the NFR2 outbox invariant even when no ledger movement occurs. *(MVP: the `audit_log` row originally specified for block paths is deferred — see §8.)*
- **FR2.4:** User suspension (auto only in MVP) — when a user accumulates repeated fraud rule breaches within a tunable window (see §14 `app.fraud.suspension.*`), the async fraud consumer MUST flip `user.fraud_status` from `ACTIVE` to `SUSPENDED`. Subsequent money mutations from a suspended user are rejected by the sync pre-check with HTTP 403 `account.suspended` (error key preserved for API compat). *(MVP: manual unsuspend is deferred — there is no `FRAUD_ANALYST` role and no `audit_log` entry to record justification. When manual unsuspend ships, both come back together via ADR.)*
- **FR2.5:** Alert stream — every rule breach (block + automatic suspension) MUST publish a record to Kafka `fraud-alerts` for the admin dashboard (FR3.2). Blocking the user does not replace dashboard notification — both happen.

### Epic 3: Real-time Admin Dashboard

- **FR3.1:** Live metrics — total daily transaction count and total daily volume rendered in real time.
- **FR3.2:** Alert stream — fraud alerts pushed over WebSocket as toast notifications without page reload.

### Epic 4: AI-Driven Personal Finance Management

- **FR4.1:** Multi-bucket budgeting — users create monthly budget plans per category.
- **FR4.2:** Real-time budget updates — the PFM consumer drains `transaction-events` and adjusts the corresponding bucket automatically.
- **FR4.3:** Threshold setup — users can set soft warning levels (e.g. notify at 80% of the Shopping budget).

### Epic 5: PFM Notifications

- **FR5.1:** Threshold breach alerts — push / WebSocket notifications when a budget hits its configured limit.
- **FR5.2:** Predictive warning — evaluate current burn rate to warn early (e.g. "At this pace, your Food budget runs out before day 20").

### Epic 6: AI Advisor

- **FR6.1:** End-of-month analysis — aggregate planned vs. actual spending and send an anonymised prompt to the LLM.
- **FR6.2:** Personalised advice — return tailored financial advice from the LLM.
- **FR6.3:** Auto-adjust plan — LLM suggests an optimised budget structure for the next month based on prior-month habits.

---

## §6 Non-functional requirements / invariants

| ID | Invariant | Why it matters | Enforcement layer |
|---|---|---|---|
| NFR1 | **Hybrid concurrency**: Redis distributed lock keyed on `wallet_id` is acquired first (short TTL, fences out concurrent retries / spam clicks); the inner DB transaction then uses `SELECT … FOR UPDATE` via JPA `PESSIMISTIC_WRITE` to lock the ledger row. The Redis lock fails fast; the DB lock is authoritative | Prevent race conditions on the ledger AND prevent hot-row pile-up by rejecting duplicate retries at the edge | Service layer + Redis lock helper + repository |
| NFR2 | ACID-strict writes via `@Transactional`; cross-system consistency via the **Transactional Outbox Pattern** — the DB row and the outbox row are committed in the same transaction, and a Quarkus `@Scheduled` poller drains the outbox into Kafka with at-least-once delivery semantics (consumers are idempotent) | Money cannot be created or destroyed; events cannot be lost or duplicated relative to DB state | Service layer + scheduled outbox poller |
| NFR3 | Transfer endpoints require an `Idempotency-Key` HTTP header; replays return the original outcome | Retry safety against network flakiness or spam clicks | Idempotency middleware (shared module) |
| NFR4 | ≥80% line coverage on service layer with JUnit 5 + Mockito | Regression safety | JaCoCo gate in CI |
| NFR5 | The HTTP path MAY perform fast, bounded Redis-counter pre-checks (fraud velocity / volume per FR2.1–FR2.2, plus `user.fraud_status` lookup per FR2.4) but MUST NOT run heavy fraud / PFM / dashboard analytics inline. Cross-event fraud analysis, alert fan-out, suspension policy, PFM aggregation, and dashboard aggregation MUST run in separate Kafka-consumer threads | Block visibly fraudulent activity at the edge without coupling the request thread to heavy analytics | Architecture review + module boundaries |
| NFR6 | Budget state MUST NOT be maintained by direct `UPDATE`s against core tables. **CQRS dual read-model**: Redis hashes are the hot path (updated by the Kafka consumer in real time); a Postgres materialized view is refreshed periodically as the durable backup and is the source of truth for rebuilding Redis after a cache loss | Prevent row-locks on Core Banking tables; scale reads independently; survive Redis flushes without data loss | Service layer + PFM consumer + scheduled MV refresh + Redis rebuild job |
| NFR7 | PFM calculations use `event_timestamp` from the Kafka payload (event time), not consumer wall-clock. Late-arriving events MUST be handled without corrupting accounting reports | Distributed-system correctness | Consumer logic with watermarks / reconciliation job |
| NFR8 | Outbound LLM calls are wrapped in a Circuit Breaker. The PFM Advisor endpoint follows Asynchronous Request-Reply (return HTTP 202 immediately, deliver the result over WebSocket) | LLM calls are slow and expensive — must never block HTTP threads | SmallRye Fault Tolerance + WebSocket reply channel |
| NFR9 | **Synchronous fraud blocking**: the sync money path MUST evaluate velocity (FR2.1), volume (FR2.2), and `user.fraud_status` (FR2.4) before opening the DB transaction. Counters live in Redis (sliding window); suspension state lives in Postgres. Async fraud analytics, alert fan-out (FR2.5), and the suspension-policy decision (when to flip a user to `SUSPENDED`) remain on the Kafka consumer | Fraud is prevented at the edge — suspicious activity never reaches the ledger — without coupling the request thread to heavy analytics | Service layer + Redis counters + Postgres `user.fraud_status` + fraud consumer for policy/alerting |

---

## §7 External integrations

| System | Direction | Protocol | Auth | Failure handling |
|---|---|---|---|---|
| LLM API (provider ❓ TBD) | outbound | HTTPS REST | API key in secrets | Circuit breaker + bulkhead; fallback to "advice unavailable, try later" message (no rule-based fallback in MVP) |

> No SMTP, payment gateway, or SSO integration in MVP — see §11.

---

## §8 Security baseline

- **Auth scheme:** JWT (stateless), signed with **ES256** (ECDSA P-256). Smaller tokens and keys than RS256, faster signing on the wallet path.
- **Authorization model:** Role-based with two roles in MVP: `USER`, `ADMIN` (see §2.2). One role per user, stored as `user.role`. The `FRAUD_ANALYST` role and multi-role-per-user are deferred and will return via ADR when manual unsuspend / analyst workflows ship.
- **PII handled:** email, wallet balance, transaction history, category labels. No card / bank credentials are stored (deposits are simulated).
- **Compliance constraints (MVP partial):** **SOC 2 alignment is a directional goal, deferred in MVP.** What MVP commits to: (a) RBAC enforced at the service layer (not only in the controller), (b) change-management traceability via Conventional Commits + PR review, (c) no PII in logs. What MVP defers: an immutable `audit_log` table covering all money mutations and admin actions (returns when manual unsuspend, role grants UI, or admin PII reads ship), formal access-review tooling.
- **Rate limiting:** applied to sensitive endpoints only via a Redis token bucket:
  - `POST /transfers` — 10 req / minute / user.
  - `POST /advisor/*` — 5 req / hour / user (LLM cost control on top of the circuit breaker in NFR8).
- **Secret management:** 12-factor env vars in MVP via `.env` (gitleaks pre-commit); production secret manager deferred.
- **HTTPS-only:** yes in production; HTTP allowed inside the local docker-compose network.
- **LLM payload sanitisation:** prompts sent to the LLM (FR6.1) must be anonymised — no user identifiers, only aggregated amounts and category labels.
- **User suspension is automatic-only in MVP:** flipping `user.fraud_status` to `SUSPENDED` is performed by the async fraud consumer under a documented policy. Manual unsuspend is deferred — it requires both the `FRAUD_ANALYST` role and the `audit_log` table to record the analyst's justification. When that ships, reintroduce both via an ADR.

---

## §9 Domain glossary

| Term | Meaning in this product |
|---|---|
| User | A signed-up identity; owns one or more wallets, one or more budgets, and zero or more fraud alerts. In MVP a user holds exactly one role (`USER` or `ADMIN`) on the `user.role` column. (Spec earlier called this "Account" — renamed to `user` in the schema; the API contract still uses `account.suspended` as the error key for backward compat.) |
| Base currency | An ISO 4217 code chosen by the user at signup and stored as `user.base_currency`. **Immutable.** Every budget owned by this user is implicitly scoped to it. Cross-currency spending is converted by the PFM consumer using the `exchange_rate` snapshotted on the `transaction` row. |
| Wallet | A balance-bearing record owned by a user, scoped to a single currency. A user MAY own multiple wallets in the same currency (no uniqueness constraint on `(user_id, currency)`); each wallet is identified by its own `wallet_id` and a user-supplied label. |
| Transfer | A two-leg atomic operation: debit the sender wallet, credit the receiver wallet. If currencies differ, the snapshotted `exchange_rate` records the rate used. |
| Transaction | One **leg** of a wallet movement — one row in the `transaction` table. A deposit and a withdraw produce one row each; a transfer produces two rows (debit + credit) sharing a `transfer_id`. The 4-value API filter (`deposit` / `withdraw` / `transfer_debit` / `transfer_credit`) is derived from `type` × `direction`. |
| FX rate | A `(from_currency, to_currency) → rate` entry. Source of truth is the `fx_rate` table (static seed via Flyway, mutable through an admin-only path). Read-through cached in Redis with a TTL. Used at transfer time only; never used to revalue stored balances. |
| Deposit | A one-leg credit to a wallet (simulated funding). |
| Withdraw | A one-leg debit from a wallet (simulated cash-out). |
| Category | A user-facing label attached to a transaction (Food, Entertainment, …) used by PFM. Seeded via Flyway; `category.id` is `int` (small reference table). |
| Budget | A monthly per-user spending plan, scoped to the user's `base_currency`. UNIQUE per `(user_id, month)`. |
| Bucket | One row of a budget — `(budget_id, category_id, planned_amount, threshold_percent?)`. The spent amount lives in Redis (hot) + Postgres materialized view (durable backup) per NFR6, not on the `budget_bucket` table. |
| Threshold | A soft warning level on a bucket as an integer percent in `[1, 100]` (e.g. 80% of `planned_amount`). NULL when the user has not set one. |
| Idempotency Key | Client-supplied UUID guaranteeing at-most-once side effects on a mutating endpoint. Tracked in the `idempotency_record` table with `(user_id, endpoint, idempotency_key)` UNIQUE. |
| Outbox | A DB table written in the same transaction as a money mutation; a `@Scheduled` poller drains rows where `published_at IS NULL` to Kafka. |
| Event time | The `event_timestamp` carried in the Kafka payload (NFR7), distinct from the consumer's processing time and from the `created_at` DB-insert column. |
| Velocity | Number of transactions per user per unit time (input to FR2.1). |
| Volume | Cumulative transaction amount per user per unit time (input to FR2.2). |
| Fraud counter | Redis sliding-window counter (per user, per rule — velocity / volume) read by the sync pre-check (NFR9) to decide whether a candidate transaction would breach a threshold. |
| Fraud status | User-level enum (`ACTIVE`, `SUSPENDED`) stored on `user.fraud_status`. `SUSPENDED` users are rejected by the sync money path with error key `account.suspended` (preserved for API back-compat). Set by the async fraud consumer; **manual unsuspend is deferred in MVP**. |
| Fraud block | A money mutation rejected synchronously by the fraud pre-check (NFR9). Persists an outbox `transaction.blocked` event — no ledger row is written. *(MVP: the `audit_log` row originally specified for block paths is deferred — see §8.)* |

---

## §10 Open architectural decisions (ADRs to write)

> Each row below corresponds to a decision that has been **made** and now needs an ADR document capturing the chosen option, the alternatives considered, and the rationale. The single open item is ADR #2.

| # | Decision | Chosen | Status |
|---|---|---|---|
| 1 | JWT signing algorithm | ES256 (ECDSA P-256) | ✅ Decided — write ADR |
| 2 | LLM provider for PFM advisor | ❓ Open — Claude / OpenAI / Gemini / local | ⏳ Blocking FR6.x |
| 3 | Concurrency strategy | Hybrid: Redis distributed lock (outer) + DB `SELECT … FOR UPDATE` (inner) | ✅ Decided — write ADR |
| 4 | CQRS read-model for budgets | Both: Redis (hot path) + Postgres materialized view (durable backup, rebuild source) | ✅ Decided — write ADR |
| 5 | Outbox publisher | Transactional outbox + Quarkus `@Scheduled` poller (no Debezium) | ✅ Decided — write ADR |
| 6 | Multi-currency model | Multiple wallets per user, each scoped to a single currency; a user MAY own several wallets in the same currency. Cross-currency transfers convert at transfer time using a cached FX rate. Each user has an immutable `base_currency` chosen at signup that scopes budget reporting | ✅ Decided — write ADR |
| 7 | Build tool | Maven | ✅ Decided — write ADR |
| 8 | Frontend stack | React 18 + TypeScript strict + Tailwind + Redux Toolkit + React Hook Form + Zod + pnpm + Vitest + Playwright | ✅ Decided — write ADR |
| 9 | RBAC roles (MVP) | `USER`, `ADMIN` (two roles, stored on `user.role`). `FRAUD_ANALYST` and multi-role-per-user are deferred — see ADR #9 MVP scope notes | ✅ Decided — write ADR |
| 10 | Fraud enforcement model | Hybrid: synchronous Redis-counter pre-check + `user.fraud_status` lookup on the money path (blocks the user); async Kafka consumer for alerts and the auto-suspension policy decision. Manual unsuspend deferred in MVP | ✅ Decided — write ADR |

---

## §11 Explicit non-goals (out of scope)

- Real bank integration / settlement — deposits and withdrawals are simulated.
- Card or KYC onboarding.
- Mobile native apps — web only in MVP.
- SSO with external IdPs in MVP.
- **Live FX rates** — cross-currency transfers use a static `fx_rates` table seeded via Flyway (admin-mutable). No external FX provider, no real-time market rates.
- Rule-based fallback for the AI advisor — if the LLM circuit is open, the advisor surfaces a clear "unavailable" state instead of degrading to heuristics.

---

## §12 Development workflow

- **Branch model:** trunk-based (short-lived feature branches off `main`).
- **Default branch:** `main`.
- **PR / MR style:** GitHub pull requests.
- **Commit convention:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Code review:** at least one reviewer required; blocking checks in CI = compile + unit tests + integration tests (Testcontainers) + JaCoCo coverage gate (≥80% service layer) + frontend lint + frontend tests.
- **Pre-commit hooks:** gitleaks (secret scan), formatter (Spotless on backend, Prettier on frontend), and a fast lint pass.
- **Coverage gate in CI:** yes — fail under 80% on the service layer (NFR4).

---

## §13 Coding conventions (highest-level, project-wide)

- **Naming:** Java classes PascalCase, methods/fields camelCase, constants UPPER_SNAKE; TypeScript files kebab-case, components PascalCase; SQL identifiers snake_case.
- **DI / IoC style:** Constructor injection only on the backend; no field injection.
- **Error model:** Typed exception hierarchy rooted at a project-level `DomainException` carrying an `error_key` + human message; mapped to HTTP responses by a single JAX-RS exception mapper.
- **Logging library:** SLF4J via JBoss Logging (Quarkus default). Levels: `INFO` for business events, `WARN` for recoverable degradations, `ERROR` for unrecovered failures, `DEBUG` for traceable execution detail. No PII in logs.
- **DTOs:** DTOs are separate from JPA entities. Entities are never serialised over the API.
- **Documentation:** OpenAPI generated by the Quarkus SmallRye OpenAPI extension; hand-maintained at the resource layer.
- **Money / currency:** `BigDecimal` in Java, `numeric(19,4)` in Postgres, currency code as ISO 4217 (`USD`, `EUR`, …). Never `double`/`float` for money.
- **Timestamps:** UTC everywhere, stored as `timestamptz`, serialised as ISO-8601.

---

## §14 Environment & configuration

| Property | Env variable | Purpose | Default | Profiles |
|---|---|---|---|---|
| `quarkus.datasource.jdbc.url` | `DB_URL` | Postgres JDBC URL | — | dev / test / prod |
| `quarkus.datasource.username` | `DB_USER` | Postgres user | — | dev / test / prod |
| `quarkus.datasource.password` | `DB_PASSWORD` | Postgres password | — | dev / test / prod |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | — | dev / test / prod |
| `quarkus.redis.hosts` | `REDIS_URL` | Redis endpoint | — | dev / test / prod |
| `app.jwt.public-key` | `JWT_PUBLIC_KEY` | JWT verification key | — | dev / test / prod |
| `app.jwt.private-key` | `JWT_PRIVATE_KEY` | JWT signing key | — | dev / prod |
| `app.llm.api-key` | `LLM_API_KEY` | LLM provider API key | — | dev / prod |
| `app.llm.base-url` | `LLM_BASE_URL` | LLM provider endpoint | — | dev / prod |
| `app.fraud.velocity.window-seconds` | `FRAUD_VELOCITY_WINDOW_SECONDS` | Velocity check window (FR2.1) | `60` | all |
| `app.fraud.velocity.threshold` | `FRAUD_VELOCITY_THRESHOLD` | Max txns per window | `5` | all |
| `app.fraud.volume.window-seconds` | `FRAUD_VOLUME_WINDOW_SECONDS` | Volume check window (FR2.2) | `3600` | all |
| `app.fraud.volume.threshold` | `FRAUD_VOLUME_THRESHOLD` | Max cumulative volume per window (USD-equivalent) | `50000` | all |
| `app.fraud.suspension.breach-count` | `FRAUD_SUSPENSION_BREACH_COUNT` | Number of FR2.1 / FR2.2 breaches within the suspension window before the async consumer flips `user.fraud_status` to `SUSPENDED` (FR2.4) | `3` | all |
| `app.fraud.suspension.window-seconds` | `FRAUD_SUSPENSION_WINDOW_SECONDS` | Sliding window over which suspension-triggering breaches are counted (FR2.4) | `3600` | all |
| `app.ratelimit.transfer.per-minute` | `RATELIMIT_TRANSFER_PER_MINUTE` | Per-user transfer rate cap | `10` | all |
| `app.ratelimit.advisor.per-hour` | `RATELIMIT_ADVISOR_PER_HOUR` | Per-user LLM advisor rate cap | `5` | all |
| `app.fx.rate-ttl-seconds` | `FX_RATE_TTL_SECONDS` | Redis TTL for cached FX rates | `300` | all |

---

## §15 Reference materials

- **Product spec (PRD):** the FR/NFR summary provided 2026-05-12 (source for this file).
- **Design / wireframes:** ❓ TBD — none yet.
- **Prior art / similar systems:** ❓ TBD.
- **External standards:** ISO 4217 (currency codes), ISO 8601 (timestamps), RFC 7519 (JWT), RFC 9457 (Problem Details for HTTP APIs — candidate for error envelope).

---

## §16 Open questions to answer before bootstrapping

| # | Question | Status | Notes |
|---|---|---|---|
| 1 | LLM provider for the PFM advisor? | ❓ Unanswered | Only remaining blocker for FR6.x — ADR #2. Candidates: Anthropic Claude / OpenAI / Gemini / local. |
| 2 | JWT signing algorithm? | ✅ Answered | ES256 — ADR #1 |
| 3 | Concurrency strategy? | ✅ Answered | Hybrid Redis lock + DB `FOR UPDATE` — ADR #3 |
| 4 | CQRS read-model for budgets? | ✅ Answered | Redis (hot) + materialized view (backup) — ADR #4 |
| 5 | Outbox publisher? | ✅ Answered | Transactional outbox + Quarkus scheduled poller — ADR #5 |
| 6 | Multi-currency model? | ✅ Answered | Multiple wallets per user, each scoped to a single currency; a user MAY own several wallets in the same currency. FX at transfer time via cached rate. Each user has an immutable `base_currency` chosen at signup — ADR #6 |
| 7 | Build tool? | ✅ Answered | Maven — ADR #7 |
| 8 | Frontend stack? | ✅ Answered | React + TS strict + Tailwind + RTK + RHF/Zod + pnpm + Vitest + Playwright — ADR #8 |
| 9 | RBAC roles in MVP? | ✅ Answered | Two roles in MVP: `USER`, `ADMIN`, stored as a column on the `user` row. `FRAUD_ANALYST` and multi-role-per-user deferred — see ADR #9 |
| 10 | Repo remote + CI? | ✅ Answered | GitHub + GitHub Actions |
| 11 | License? | ✅ Answered | Proprietary |
| 12 | Compliance constraints? | ✅ Answered (partial) | SOC 2 directional goal; MVP commits to RBAC at service layer + no PII in logs. Immutable `audit_log` table and formal access review deferred — see §8 |
| 13 | Rate-limiting policy? | ✅ Answered | Token bucket on `/transfers` (10/min/user) and `/advisor/*` (5/hour/user) |
| 14 | FX rate source? | ✅ Answered | Static seed in DB table `fx_rate` (loaded via Flyway migration); no external FX provider in MVP. Cached in Redis with TTL on read. |
| 15 | LLM payload retention / training opt-out (provider-dependent)? | ❓ Unanswered | Resolve together with ADR #2 |
| 16 | Suspension un-block policy: manual-only, or auto-clear after a TTL? | ✅ Answered (MVP) | **Deferred for MVP** — only automatic suspension ships. Manual unsuspend returns together with `FRAUD_ANALYST` role and the `audit_log` table via ADR superseding §8 |

---

## §17 Optional sections

### 17.1 Performance budget

> MVP dev-target budget. Single-node `docker-compose` stack on a developer machine — not a production SLO. Revisit once observability (§17.2) lands and we have real measurements.

| Endpoint / scenario | P95 latency | Throughput |
|---|---|---|
| `POST /transfers` (sync path: idempotency + rate limit + **fraud pre-check** + wallet lock + DB commit) | ≤ 200 ms | ≥ 100 rps single-node dev target |
| `POST /advisor/analyze` (returns 202) | ≤ 100 ms | — |
| WebSocket alert fan-out (ingest → broadcast) | ≤ 1 s | — |
| Async suspension propagation (`user.fraud_status = SUSPENDED` visible to sync path) | ≤ 1 s | — |

> The fraud pre-check (NFR9) is intentionally inside the `/transfers` budget. It is bounded — two Redis sliding-window lookups plus one `user.fraud_status` read — so the existing ≤ 200 ms target holds. If observability later shows the pre-check eating more than ~10 ms P95, revisit the counter implementation before relaxing the budget.

### 17.2 Observability requirements

- **Metrics sink:** ❓ TBD — Micrometer + Prometheus suggested (Quarkus has first-class support).
- **Traces:** ❓ TBD — OpenTelemetry suggested, especially across Kafka boundaries.
- **Logs:** JSON to stdout; aggregation tool deferred.

### 17.3 Accessibility floor

- **WCAG level:** ❓ TBD — recommend WCAG 2.1 AA for the user app and admin dashboard.
- **Keyboard-only paths:** every interactive element.
- **Screen-reader support:** required for the user app; nice-to-have for the admin dashboard.

---

*End of project-info. Items in §16 must be resolved (or explicitly deferred) before generating CLAUDE.md and the rule set.*
