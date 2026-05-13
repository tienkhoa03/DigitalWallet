# Architecture

This page describes the system's high-level shape, module layout, integration wires, and configuration surface. The authoritative input is [../../project-info.md](../../project-info.md) §§3, 4, 8, 14.

## 1. What it is

DigitalWallet is a multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager (PFM) that learns from each user's spending stream ([../../project-info.md §1](../../project-info.md#1-project-identity)). The system is shaped as a **modular monolith with a two-stream architecture**: a synchronous transactional core that commits the authoritative ledger on the request thread, and one or more asynchronous Kafka consumers (Fraud, PFM, Admin Dashboard, AI Advisor) that derive insights without coupling them to the money path. The primary value the project proves is that a single event-driven backbone can serve both a strict ACID money ledger and a derived, AI-augmented analytics stream without coupling them on the request thread.

## 2. Tech stack at a glance

```
            +-----------------------------+
            |  React 18 + TS strict       |
            |  Redux Toolkit + RTK Query  |
            |  Tailwind 3 + RHF + Zod     |
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

`(spec — not yet implemented)` — the layout below is the target from [../../project-info.md §3.1](../../project-info.md#31-module--package-organization).

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

**Organising principle:** Feature-based + layered. Group code by feature module under `backend/`; inside each module the standard layers `api/`, `service/`, `persistence/` (plus `consumer/` and `event/` where applicable) are kept separate. The `shared/` module holds cross-cutting concerns (money type, idempotency middleware, outbox poller, security).

## 4. Frontend layering

`(spec — not yet implemented)` — [../../project-info.md §3.1](../../project-info.md#31-module--package-organization) names a single React app under `frontend/` that serves both end users and the admin dashboard; the internal directory split is not yet committed. The frontend stack is mandated in [../../project-info.md §4.2](../../project-info.md#42-frontend) and recorded in [../decisions/0008-frontend-stack.md](../decisions/0008-frontend-stack.md).

Once the internal split lands, it is expected to reflect at minimum:

- a routed shell that hosts both the user-facing app and the admin dashboard,
- RTK Query API slices per backend module,
- a WebSocket client wrapper consumed by both the alert stream (FR3.2) and the advisor reply channel (NFR8),
- shared form primitives wired to React Hook Form + Zod.

## 5. How modules connect

| Concern | Convention | Source |
|---|---|---|
| API base URL | REST over HTTPS in production; HTTP allowed inside the docker-compose network. Path conventions to be set in [../api/README.md](../api/README.md). | [../../project-info.md §8](../../project-info.md#8-security-baseline) |
| Wire format | JSON; OpenAPI generated by Quarkus SmallRye OpenAPI extension. | [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide) |
| Auth scheme | Stateless JWT signed with ES256. See §6 below. | [../../project-info.md §8](../../project-info.md#8-security-baseline), [../decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md) |
| Authorization | RBAC enforced at the service layer (not only in the controller). Three roles: `USER`, `ADMIN`, `FRAUD_ANALYST`. | [../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system), [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md) |
| Idempotency | `Idempotency-Key` HTTP header on every mutating transfer/deposit/withdraw endpoint; replays return the original outcome. | [../../project-info.md §6 NFR3](../../project-info.md#6-non-functional-requirements--invariants) |
| Real-time channel | Native WebSocket — fraud alerts (FR3.2), budget alerts (FR5.1), advisor async reply (NFR8). | [../../project-info.md §3](../../project-info.md#3-architecture-style), [../../project-info.md §4.2](../../project-info.md#42-frontend) |
| Money path → analytics | Transactional Outbox Pattern: the ledger row and the outbox row commit in the same DB transaction; a Quarkus `@Scheduled` poller drains the outbox into `transaction-events`. The HTTP handler does not publish to Kafka. | [../../project-info.md §6 NFR2/NFR5](../../project-info.md#6-non-functional-requirements--invariants), [../decisions/0005-outbox-publisher.md](../decisions/0005-outbox-publisher.md) |
| Concurrency | Outer Redis distributed lock keyed on `wallet_id` (short TTL, fail-fast) + inner DB `SELECT … FOR UPDATE` via JPA `PESSIMISTIC_WRITE`. | [../../project-info.md §6 NFR1](../../project-info.md#6-non-functional-requirements--invariants), [../decisions/0003-concurrency-strategy.md](../decisions/0003-concurrency-strategy.md) |
| Rate limiting | Redis token bucket: `POST /transfers` 10/min/user, `POST /advisor/*` 5/hour/user. | [../../project-info.md §8](../../project-info.md#8-security-baseline) |
| Error envelope | Typed exception hierarchy rooted at `DomainException(error_key, message)`, mapped to HTTP by a single JAX-RS exception mapper. Canonical envelope in [../api/README.md](../api/README.md#error-response-shape). | [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide) |

## 6. Auth flow

`(spec — not yet implemented)` — implementation pending; the spec already commits the scheme.

- **Token format:** JWT (RFC 7519), signed with **ES256** (ECDSA P-256). See [../decisions/0001-jwt-signing-algorithm.md](../decisions/0001-jwt-signing-algorithm.md).
- **Authorization model:** role-based. Three roles `USER`, `ADMIN`, `FRAUD_ANALYST` ([../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md)). RBAC is enforced at the service layer to align with SOC 2.
- **Audit log:** dedicated append-only table covers authentication events, role grants, transfers, fraud-rule changes, and admin reads of user data ([../../project-info.md §8](../../project-info.md#8-security-baseline)).
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
| `app.ratelimit.transfer.per-minute` | `RATELIMIT_TRANSFER_PER_MINUTE` | Per-user transfer rate cap | `10` | all |
| `app.ratelimit.advisor.per-hour` | `RATELIMIT_ADVISOR_PER_HOUR` | Per-user LLM advisor rate cap | `5` | all |
| `app.fx.rate-ttl-seconds` | `FX_RATE_TTL_SECONDS` | Redis TTL for cached FX rates | `300` | all |

## 8. Deployment topology

The stack is packaged as Docker containers and orchestrated via Docker Compose on a single host for both local development and demo. The compose stack runs the Quarkus backend, Postgres 16, Kafka, Redis 7, and the frontend dev server. No Kubernetes target is in scope for MVP. CI/CD is GitHub Actions: build, unit + integration tests via Testcontainers, JaCoCo coverage gate (≥80% service-layer line coverage), and frontend lint + tests ([../../project-info.md §4.6](../../project-info.md#46-deployment)).
