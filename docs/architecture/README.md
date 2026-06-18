# Architecture

This page describes the system's high-level shape, module layout, integration wires, and configuration surface. The authoritative input is [../../project-info.md](../../project-info.md) §§3, 4, 8, 14.

## 1. What it is

DigitalWallet is a multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager (PFM) that learns from each user's spending stream ([../../project-info.md §1](../../project-info.md#1-project-identity)). The system is shaped as a **modular monolith with a two-stream architecture**: a synchronous transactional core that commits the authoritative ledger on the request thread (after a bounded fraud pre-check at the edge — velocity, volume, and `account.fraud_status`, per [../../project-info.md §6 NFR9](../../project-info.md#6-non-functional-requirements--invariants)), and one or more asynchronous Kafka consumers (Fraud, PFM, Admin Dashboard, AI Advisor) that derive insights and own deeper fraud analysis, alerting, and suspension-policy decisions without coupling them to the money path. The primary value the project proves is that a single event-driven backbone can serve both a strict ACID money ledger and a derived, AI-augmented analytics stream without coupling them on the request thread.

## 2. Tech stack at a glance

```
            +-----------------------------+
            |  Vue 3 + TS strict          |
            |  Pinia + Vue Query          |
            |  Tailwind 3 + VeeValidate   |
            |  + Zod                      |
            +--------------+--------------+
                           |
              REST (HTTPS) | WebSocket
                           v
            +-----------------------------+
            |  Quarkus 3.x (Java 21)      |
            |  JAX-RS (RESTEasy Reactive) |
            |  Hibernate ORM + Panache    |
            |  SmallRye Reactive Msging   |
            |  SmallRye Fault Tolerance   |
            +-+--------+---------+--------+
              |        |         |
   PESSIMISTIC|  Token |Outbox   | Circuit-broken HTTPS
   _WRITE     |  bucket|poller   v
              |  /lock |    +---------+
              v        |    |  LLM    | (provider TBD — ADR #2)
       +-----------+   |    +---------+
       | Postgres  |   |
       |   16      |   |
       |  (ledger, |   |
       |  outbox,  |   |
       |  MV)      |   |
       +-----+-----+   |
             |         |
             |   +-----v-----+
             |   |  Redis 7  | (locks, idempotency, rate-limit, hot read-model)
             |   +-----+-----+
             |         |
             v         v
       +-----------------------------+
       |   Kafka                     |
       |   transaction-events        |
       |   fraud-alerts              |
       |   pfm-threshold-alerts      |
       |   advisor-requests/responses (TBD)
       +--------------+--------------+
                      |
        +-------------+-------------+-----------------+
        v             v             v                 v
   +---------+   +---------+   +---------+      +---------+
   |  Fraud  |   |  PFM    |   |Dashboard|      | Advisor |
   | consumer|   | consumer|   | consumer|      | consumer|
   +---------+   +---------+   +---------+      +---------+
                                      \
                                       --> WebSocket fan-out --> Frontend
```

Sources: [../../project-info.md §3](../../project-info.md#3-architecture-style), [../../project-info.md §4](../../project-info.md#4-tech-stack-mandated).

## 3. Backend layering

Module skeleton scaffolded under `backend/src/main/java/com/digitalwallet/` (Phase A–E of the bootstrap); only `shared/` carries production code today. The layout below is the target from [../../project-info.md §3.1](../../project-info.md#31-module--package-organization).

```
DigitalWallet/
├── backend/                       # Quarkus application + its deploy tier
│   ├── Dockerfile                 # multi-stage JVM (eclipse-temurin:21-jre)
│   ├── docker-compose.yml         # Postgres 16 + Kafka KRaft + Redis 7 + (--profile app) backend
│   ├── env.template               # backend + infra env (DB / Kafka / Redis / JWT / LLM / fraud)
│   ├── postgres/init/             # init scripts (test DB bootstrap)
│   ├── account/                   # FR1.1 — one hexagon (signup, role, base_currency, fraud_status)
│   │   ├── domain/                #   framework-free model + rules
│   │   ├── application/           #   port/in (use cases) · port/out (SPIs) · service (use-case impls)
│   │   └── adapter/               #   in/web · in/messaging · out/persistence
│   ├── wallet/                    # FR1.2, FR1.3, FR1.4
│   │   ├── domain/  application/  adapter/   # in/web · out/persistence · out/messaging
│   ├── fraud/                     # FR2.1, FR2.2, FR2.3, FR2.4, FR2.5
│   │   ├── domain/  application/  adapter/   # in/messaging · out/redis · out/messaging
│   ├── pfm/                       # FR4.x, FR5.x
│   │   ├── domain/  application/  adapter/   # in/web · in/messaging · out/redis (NO ledger persistence — NFR6)
│   ├── advisor/                   # FR6.x — LLM integration
│   │   ├── domain/  application/  adapter/   # in/web · in/messaging · out/llm
│   ├── dashboard/                 # FR3.x
│   │   ├── domain/  application/  adapter/   # in/web · in/messaging (WebSocket) 
│   └── shared/                    # domain kernel + cross-cutting adapters: money, idempotency, outbox poller, rate-limit, lock, security, exception mapper
└── frontend/                      # Vue 3 app (user app + admin dashboard) + its deploy tier
    ├── Dockerfile                 # multi-stage Node 20 build → nginx 1.27
    ├── docker-compose.yml         # nginx serving dist/, joins the backend's dw-net
    ├── nginx.conf                 # static + /api reverse-proxy + WebSocket upgrade
    └── env.template               # public-only config (VITE_* is readable in the browser)
```

**Organising principle:** Per-module hexagonal (ports & adapters) within a modular monolith. Each feature module under `backend/` is its own hexagon: a framework-free `domain/`, an `application/` layer holding inbound ports (use cases), outbound ports (SPIs), and the use-case implementations (application services), and an `adapter/` layer split into inbound adapters (`in/web` JAX-RS, `in/messaging` Kafka) and outbound adapters (`out/persistence` JPA, `out/redis`, `out/messaging`, `out/llm`). Dependencies point inward only; frameworks live in adapters. The `shared/` module holds the domain kernel (money type), the exception/security/validation infrastructure, and the cross-cutting outbound adapters (idempotency, outbox poller, rate-limit, Redis lock).

**Dependency rule:** dependencies point INWARD only — `adapter` → `application` → `domain`. `domain/` depends on nothing; `application/` depends only on `domain/` and its own ports; `adapter/` depends on `application/` (ports) and `domain/`. Frameworks (JAX-RS, Hibernate/Panache, SmallRye Kafka, the Redis client, MicroProfile config, SmallRye Fault Tolerance) appear ONLY in `adapter/` packages — `domain/` and `application/` are framework-free. Application services depend on outbound ports (`port/out`), never on concrete adapters; the outbound adapter `implements` the port.

## 4. Frontend layering

Frontend scaffolded under `frontend/src/` (Phase A–E): `app/` (`App.vue` routed shell + Pinia store wiring + Vue Query plugin), `features/` (per-module folders), `routes/` (route table + guards), `shared/` (UI primitives, WebSocket client, money helpers). Per [../../project-info.md §3.1](../../project-info.md#31-module--package-organization), a single Vue 3 app serves both end users and the admin dashboard. The frontend stack is mandated in [../../project-info.md §4.2](../../project-info.md#42-frontend) and recorded in [../decisions/0008-frontend-stack.md](../decisions/0008-frontend-stack.md).

Once the internal split lands, it is expected to reflect at minimum:

- a routed shell (`App.vue`) that mounts the router and wires the Pinia store and the Vue Query plugin, hosting both the user-facing app and the admin dashboard,
- Vue Query API clients per backend module,
- a WebSocket client wrapper consumed by both the alert stream (FR3.2) and the advisor reply channel (NFR8),
- shared form primitives wired to VeeValidate + Zod.

## 5. How modules connect

| Concern | Convention | Source |
|---|---|---|
| API base URL | REST over HTTPS in production; HTTP allowed inside the docker-compose network. Path conventions to be set in [../api/README.md](../api/README.md). | [../../project-info.md §8](../../project-info.md#8-security-baseline) |
| Wire format | JSON; OpenAPI generated by Quarkus SmallRye OpenAPI extension. | [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide) |
| Auth scheme | Stateless JWT signed with ES256. See §6 below. | [../../project-info.md §8](../../project-info.md#8-security-baseline), [../decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) |
| Authorization | RBAC enforced in the application service (use case), not only in the inbound web adapter. Two roles in MVP: `USER`, `ADMIN`, stored on `account.role`. `FRAUD_ANALYST` and multi-role-per-account are deferred — see ADR #9. | [../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system), [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md) |
| Idempotency | `Idempotency-Key` HTTP header on every mutating transfer/deposit/withdraw endpoint; replays return the original outcome. | [../../project-info.md §6 NFR3](../../project-info.md#6-non-functional-requirements--invariants) |
| Real-time channel | Native WebSocket — fraud alerts (FR3.2), budget alerts (FR5.1), advisor async reply (NFR8). | [../../project-info.md §3](../../project-info.md#3-architecture-style), [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Money path → analytics | Transactional Outbox Pattern: the ledger row and the outbox row commit in the same DB transaction; a Quarkus `@Scheduled` poller drains the outbox into `transaction-events`. The HTTP handler does not publish to Kafka. | [../../project-info.md §6 NFR2/NFR5](../../project-info.md#6-non-functional-requirements--invariants), [../decisions/0005-outbox-publisher.md](../decisions/0005-outbox-publisher.md) |
| Synchronous fraud blocking | Bounded Redis sliding-window counter lookups (velocity FR2.1, volume FR2.2) plus `account.fraud_status` read (FR2.4) before the wallet lock; breach rejects inline with `fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended` (error key preserved for API back-compat). Blocked attempts persist a `transaction.blocked` outbox event in a short transaction (no ledger row, no wallet lock). Async analytics, alerts (FR2.5), and the suspension-policy decision remain on the Kafka consumer. *(MVP defers `audit_log` — see ADR #9.)* | [../../project-info.md §6 NFR9](../../project-info.md#6-non-functional-requirements--invariants), [../decisions/0010-fraud-enforcement-model.md](../decisions/0010-fraud-enforcement-model.md) |
| Concurrency | Outer Redis distributed lock keyed on `wallet_id` (short TTL, fail-fast) + inner DB `SELECT … FOR UPDATE` via JPA `PESSIMISTIC_WRITE`. | [../../project-info.md §6 NFR1](../../project-info.md#6-non-functional-requirements--invariants), [../decisions/0003-concurrency-strategy.md](../decisions/0003-concurrency-strategy.md) |
| Rate limiting | Redis token bucket: `POST /transfers` 10/min/user, `POST /advisor/*` 5/hour/user. | [../../project-info.md §8](../../project-info.md#8-security-baseline) |
| Error envelope | Typed exception hierarchy rooted at `DomainException(error_key, message)`, mapped to HTTP by a single JAX-RS exception mapper. Canonical envelope in [../api/README.md](../api/README.md#error-response-shape). | [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide) |

## 6. Auth flow

`(spec — not yet implemented)` — implementation pending; the spec already commits the scheme.

- **Token format:** JWT (RFC 7519), signed with **ES256** (ECDSA P-256). See [../decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md).
- **Authorization model:** role-based. Two roles in MVP — `USER`, `ADMIN` — stored on `account.role` ([../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md)). RBAC is enforced in the application service (use case) to align with SOC 2. `FRAUD_ANALYST` and multi-role-per-account are deferred and will return via ADR when manual unsuspend / analyst workflows ship.
- **Audit log:** the immutable `audit_log` table is **deferred in MVP** ([../../project-info.md §8](../../project-info.md#8-security-baseline)). MVP commits to RBAC in the application service (use case) + no PII in logs; durable justification for privileged actions returns when manual unsuspend, role grants UI, or admin PII reads ship.
- **LLM payload sanitisation:** advisor prompts contain only aggregated amounts and category labels — no user identifiers ([../../project-info.md §8](../../project-info.md#8-security-baseline)).

## 7. Config & profiles

Source: [../../project-info.md §14](../../project-info.md#14-environment--configuration).

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
| `app.fraud.suspension.breach-count` | `FRAUD_SUSPENSION_BREACH_COUNT` | Number of FR2.1 / FR2.2 breaches within the suspension window before the async consumer flips `account.fraud_status` to `SUSPENDED` (FR2.4) | `3` | all |
| `app.fraud.suspension.window-seconds` | `FRAUD_SUSPENSION_WINDOW_SECONDS` | Sliding window over which suspension-triggering breaches are counted (FR2.4) | `3600` | all |
| `app.ratelimit.transfer.per-minute` | `RATELIMIT_TRANSFER_PER_MINUTE` | Per-account transfer rate cap | `10` | all |
| `app.ratelimit.advisor.per-hour` | `RATELIMIT_ADVISOR_PER_HOUR` | Per-account LLM advisor rate cap | `5` | all |
| `app.fx.rate-ttl-seconds` | `FX_RATE_TTL_SECONDS` | Redis TTL for cached FX rates | `300` | all |

## 8. Deployment topology

The stack is packaged as Docker containers and orchestrated via Docker Compose on a single host for both local development and demo. The two tiers are deployed independently — each owns its own `Dockerfile`, `docker-compose.yml`, and `env.template`:

| Tier | Compose file | Services | Owns network |
|---|---|---|---|
| Backend + infra | [backend/docker-compose.yml](../../backend/docker-compose.yml) | Postgres 16, Kafka KRaft (3.7), Redis 7, and the optional Quarkus container behind the `app` Compose profile | Defines `dw-net` (bridge) |
| Frontend | [frontend/docker-compose.yml](../../frontend/docker-compose.yml) | Nginx 1.27 serving the Vite build; reverse-proxies `/api` and `*/ws` upgrades to `dw-backend:8080` | Joins `dw-net` as external |

Start order is fixed: bring up the backend compose first so `dw-net` exists, then the frontend compose joins it. Local development runs the Quarkus backend via `./mvnw quarkus:dev` against just the infra services (no profile flag) for hot reload; the containerised backend (`--profile app`) is reserved for production-like smoke runs.

No Kubernetes target is in scope for MVP. CI/CD is GitHub Actions: build, unit + integration tests via Testcontainers, JaCoCo coverage gate (≥80% application-service-layer line coverage), and frontend lint + tests ([../../project-info.md §4.6](../../project-info.md#46-deployment)).
