# Implementation Plan — Initialize backend & frontend skeletons with core JPA entities

| Field | Value |
|---|---|
| **Generation date** | 2026-05-07 |
| **Issue / ticket** | — |
| **Story points** | M |
| **Milestone** | Pre-MVP scaffolding |
| **Assignees** | — |
| **Affected modules** | `backend/`, `frontend/`, repo root (`docker-compose.yml`, `.env.example`) |
| **Suggested branch name** | `feature/init-project-scaffold` |

---

## 1. Context / Problem Statement

The repository currently holds only specifications ([README.md](../../README.md), [docs/](../../docs/)) and rule files ([.claude/rules/](../../.claude/rules/)). Per [CLAUDE.md](../../CLAUDE.md), the README is the design contract and no source code, build configuration, or `docker-compose.yml` yet exists. Before any vertical slice (transfer, fraud rules, admin dashboard) can be built, the project needs a Maven Quarkus backend skeleton, an Angular 17+ frontend skeleton, persistence wiring (PostgreSQL + Flyway via `quarkus-flyway`), and a Compose stack for the supporting infrastructure (Postgres, Kafka, Redis).

This plan is intentionally narrow: it stands up the scaffold and the **core data model** ([docs/database/README.md §1](../../docs/database/README.md)) — `accounts`, `wallets`, `transaction_history` — as JPA entities + Flyway migrations only. **No service-layer logic, no JAX-RS resources, no Kafka producers, no UI components are introduced.** This boundary is deliberate so subsequent feature plans can land each vertical slice through `Skill("backend-create-rest-api")` against an already-validated foundation.

The runtime baseline is locked to **Java 21 + Quarkus 3.x LTS** ([ADR-0001](../../docs/decisions/0001-quarkus-over-spring-boot.md), [upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md)). Frontend is **Angular 17+ standalone components** ([frontend_coding.md §1.1](../../.claude/rules/frontend_coding.md), [upgrade-policy.md §4.1](../../.claude/rules/upgrade-policy.md)). Base Java package: **`com.digitalwallet`**.

---

## 2. Scope

### In Scope

- Create top-level `backend/` and `frontend/` directories.
- Initialize `backend/` as a Maven Quarkus project (created via the Quarkus Maven plugin / archetype): `pom.xml` importing `quarkus-bom`, with extensions `quarkus-resteasy`, `quarkus-resteasy-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-narayana-jta`, `quarkus-smallrye-reactive-messaging-kafka`, `quarkus-redis-client`, `quarkus-websockets-next`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`, `quarkus-junit5`. Packaging: `jar` (Quarkus runner / fast-jar).
- Create the feature-first package skeleton under `com.digitalwallet`: `wallet/`, `transaction/`, `fraud/`, `admin/`, `shared/` — each with the `api/`, `service/`, `persistence/`, `event/`, `consumer/`, `rule/`, `publisher/`, `ws/`, `config/`, `idempotency/` sub-packages that [docs/architecture/README.md §3](../../docs/architecture/README.md) defines for that feature. Sub-packages are created as empty `package-info.java` files so they survive Git.
- Create JPA entities (only — no repositories, no services, no resources):
  - `wallet/persistence/Account.java`
  - `wallet/persistence/Wallet.java`
  - `transaction/persistence/TransactionHistory.java`
- Create Flyway migrations matching the entities under `backend/src/main/resources/db/migration/`:
  - `V1__create_accounts.sql`
  - `V2__create_wallets.sql`
  - `V3__create_transaction_history.sql`
- Create `backend/src/main/resources/application.properties` with the Quarkus datasource, Hibernate ORM, and Flyway configuration keys (no `persistence.xml` is needed — Quarkus generates the persistence unit from these properties; CDI discovery is automatic so no `beans.xml` either).
- Create `.env.example` at repo root listing every variable in [docs/architecture/README.md §7](../../docs/architecture/README.md).
- Create `docker-compose.yml` at repo root with Postgres, Redis, Kafka, Zookeeper services (no backend service — backend runs from IDE / `quarkus:dev` for now).
- Initialize `frontend/` via `ng new` (standalone components, routing on, SCSS off, Tailwind added afterwards). Set up the `core/` / `features/{wallet,admin}/` / `shared/` directory layout per [frontend_coding.md §1.3](../../.claude/rules/frontend_coding.md).
- Write `backend/README.md` describing the module purpose, structure, prerequisites, and how to run (`./mvnw quarkus:dev`).

### Out of Scope

- Any `@Path` resource, service-layer class, repository, Kafka producer/consumer, WebSocket endpoint, or business rule.
- `IdempotencyKey` and `OutboxEvent` entities — gated by [ADR-0005](../../docs/decisions/0005-idempotency-key-header.md) and [ADR-0006](../../docs/decisions/0006-outbox-or-publish-after-commit.md), which leave their persistence form open.
- A `FraudAlert` JPA entity — alerts flow through Kafka + WebSocket per [docs/architecture/README.md §1](../../docs/architecture/README.md).
- A backend application service in `docker-compose.yml`.
- Authentication / authorization wiring — auth scheme is unspecified ([docs/architecture/README.md §6](../../docs/architecture/README.md)).
- Frontend feature pages, services, or routes — only the directory shell.
- CI configuration, observability stack, InfluxDB.
- Unit tests of entities (entities have no logic to test; coverage applies at the service layer per [NFR4](../../docs/testing/README.md)).

---

## 3. Open Questions

| # | Question | Source | Answer | Status |
|---|---|---|---|---|
| 1 | Quarkus baseline | [ADR-0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) | **Quarkus 3.x LTS** (current LTS at the time the scaffold lands; pin the BOM version in `pom.xml`) | ✅ Answered |
| 2 | Java base package | new | **`com.digitalwallet`** | ✅ Answered |
| 3 | Initial entity set | [docs/database/README.md §2](../../docs/database/README.md) | **Account, Wallet, TransactionHistory** (idempotency stays in Redis per [ADR-0005](../../docs/decisions/0005-idempotency-key-header.md); outbox deferred to [ADR-0006](../../docs/decisions/0006-outbox-or-publish-after-commit.md)) | ✅ Answered |
| 4 | Compose scope | [docs/architecture/README.md §8](../../docs/architecture/README.md) | **Postgres + Redis + Kafka + Zookeeper, no backend service** | ✅ Answered |
| 5 | Primary key strategy (UUID / ULID / `bigserial`) | [docs/database/README.md §3](../../docs/database/README.md), [backend_coding.md §4.2](../../.claude/rules/backend_coding.md) | **UUID v4** — defaulted per `backend_coding.md §4.2` ("Default: UUID (server-generated)"). Project-wide; do not mix. ADR to follow if revisited. | ⏳ Deferred (defaulting to UUID; revisit via ADR if performance demands change) |
| 6 | Whether `Account` lives under `wallet/` or in its own feature module | [docs/architecture/README.md §3](../../docs/architecture/README.md) only enumerates `wallet/`, `transaction/`, `fraud/`, `admin/`, `shared/` | **Place under `wallet/persistence/Account.java`** — the architecture map does not list an `account/` feature, and the wallet aggregate root is the natural owner of the account row. | ✅ Answered (defaulted) |
| 7 | Currency column on `wallets` | [docs/database/README.md §1](../../docs/database/README.md) ERD marks it `currency? (verify)` | **Omit for now** — single-currency MVP per spec; add via migration when multi-currency lands. | ⏳ Deferred |
| 8 | Specific Postgres / Kafka / Redis image versions for Compose | [docs/database/README.md §1](../../docs/database/README.md), [upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md) (`(verify)` rows) | **Postgres 16, Redis 7, Confluent Kafka 7.6** — pinned in `docker-compose.yml`; replace `(verify)` rows in `upgrade-policy.md §1` once landed. | ✅ Answered (defaulted) |

---

## 4. Technical Approach / Architecture Decisions

### 4.1 Backend build & runtime

- **Maven** with the **Quarkus Maven wrapper** (`./mvnw`) — required by [ADR-0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) and [upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md). Packaging: standard Quarkus `jar` (fast-jar runner under `target/quarkus-app/`).
- `pom.xml` imports the **Quarkus platform BOM** (`io.quarkus.platform:quarkus-bom`, version pinned to the current LTS) and declares the extensions listed in §2 In Scope. Test dependencies: `quarkus-junit5`, `mockito-core`, `assertj-core`, `org.testcontainers:postgresql`, `org.testcontainers:kafka`. Versions sourced from [upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md).
- Maven properties pin `<maven.compiler.release>21</maven.compiler.release>`.

### 4.2 Persistence wiring (Hibernate ORM with Panache)

- Configuration lives in `application.properties` — no `persistence.xml`, no `beans.xml` (Quarkus auto-discovers entities and CDI beans at build time).
  ```properties
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.jdbc.url=${QUARKUS_DATASOURCE_JDBC_URL}
  quarkus.datasource.username=${QUARKUS_DATASOURCE_USERNAME}
  quarkus.datasource.password=${QUARKUS_DATASOURCE_PASSWORD}
  quarkus.hibernate-orm.database.generation=validate
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  ```
- `quarkus.hibernate-orm.database.generation=validate` for every profile per [backend_coding.md §13.3](../../.claude/rules/backend_coding.md). Schema is owned by Flyway; ORM only validates.
- Flyway migrations live at `backend/src/main/resources/db/migration/` per [docs/database/README.md](../../docs/database/README.md). Forward-only ([backend_coding.md §13.1](../../.claude/rules/backend_coding.md)).
- This plan does NOT yet define repositories. When repositories land in the next plan, they will use `PanacheRepositoryBase<Entity, ID>` per [backend_coding.md §5.1](../../.claude/rules/backend_coding.md).

### 4.3 Entity-level conventions ([backend_coding.md §4](../../.claude/rules/backend_coding.md))

- IDs: `UUID`, server-generated via `@GeneratedValue(strategy = GenerationType.UUID)`.
- Money columns: `BigDecimal`, `numeric(19,4)`. Java type `BigDecimal`. Never `double`/`float`.
- Timestamps: `OffsetDateTime`, DB type `timestamptz`, never `java.util.Date`/`LocalDateTime`.
- `@Version` (`long`) on `Wallet` for optimistic versioning — explicitly **not** a substitute for `LockModeType.PESSIMISTIC_WRITE` (which lands when the transfer service is implemented in a later plan).
- Default `FetchType.LAZY` on every `@ManyToOne`. Explicit `@JoinColumn`.
- Entities are POJOs annotated with `@Entity` — Panache **Active Record** (extending `PanacheEntityBase`) is forbidden by [backend_coding.md §4.1](../../.claude/rules/backend_coding.md).
- DB constraint `CHECK (balance >= 0)` on `wallets.balance` per [backend_coding.md §3.4](../../.claude/rules/backend_coding.md) and [wallet-rules.md](../../docs/business-rules/) (final backstop).

### 4.4 ERD realized in this plan

```
accounts (1) ────── (n) wallets ────── (n) transaction_history
   id (uuid)          id (uuid)            id (uuid)
   identifier (uq)    account_id (fk)      wallet_id (fk)
   created_at         balance numeric(19,4) counterparty_id (uuid)
                      version              type     enum
                      created_at           direction enum
                                           amount numeric(19,4)
                                           idempotency_key uuid
                                           created_at
```

`type` enum values: `DEPOSIT | WITHDRAW | TRANSFER`. `direction` enum: `DEBIT | CREDIT` ([docs/database/README.md §1](../../docs/database/README.md)). Stored as Postgres `text` with a `CHECK` constraint — Postgres enum types are migration-hostile and forbidden by convention.

### 4.5 Frontend init

- `ng new frontend --standalone --routing --style=css --skip-git --package-manager=npm`. SCSS is intentionally off; styling is Tailwind ([frontend_coding.md §6](../../.claude/rules/frontend_coding.md)).
- Tailwind installed via `npx tailwindcss init` after `ng new`; `tailwind.config.js` content array points at `./src/**/*.{html,ts}`.
- Directory shell only: `src/app/core/`, `src/app/features/wallet/`, `src/app/features/admin/`, `src/app/shared/`, with `.gitkeep` files. No components yet.

### 4.6 Compose topology

Compose runs four services on a private network:

```
┌────────┐  ┌─────────┐  ┌──────────┐  ┌──────┐
│postgres│  │zookeeper│──│ kafka    │  │redis │
└────────┘  └─────────┘  └──────────┘  └──────┘
   :5432       :2181        :9092        :6379
```

- Postgres image `postgres:16-alpine`, named volume `postgres-data`. `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from `.env`.
- Redis image `redis:7-alpine`, default port 6379, no auth (local profile only).
- Kafka image `confluentinc/cp-kafka:7.6.0`, depends on `confluentinc/cp-zookeeper:7.6.0`.
- All ports bound to `127.0.0.1` so the local profile is not network-exposed ([security.md §5.2](../../.claude/rules/security.md)).
- No backend service — when added in a future plan, it will be a separate `quarkus-app` service (image built via `quarkus-container-image-jib`) depending on `postgres` healthcheck.

### 4.7 `.env.example`

Lists every variable from [docs/architecture/README.md §7](../../docs/architecture/README.md) with placeholder values, using the Quarkus-style env names so values can be consumed directly by `quarkus.datasource.*` etc.:

```
POSTGRES_DB=digitalwallet
POSTGRES_USER=digitalwallet
POSTGRES_PASSWORD=change-me
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/digitalwallet
QUARKUS_DATASOURCE_USERNAME=${POSTGRES_USER}
QUARKUS_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
QUARKUS_REDIS_HOSTS=redis://localhost:6379
APP_INFLUXDB_URL=
APP_INFLUXDB_TOKEN=
APP_IDEMPOTENCY_KEY_TTL_SECONDS=86400
APP_FRAUD_VELOCITY_WINDOW_SECONDS=60
APP_FRAUD_VOLUME_WINDOW_SECONDS=3600
APP_FRAUD_VOLUME_THRESHOLD=50000
```

Real `.env` is in `.gitignore` per [security.md §1.1](../../.claude/rules/security.md).

---

## 5. Applicable Rules, Skills & Agents

| Concern | Rule / Skill / Agent |
|---|---|
| Backend Maven + entity scaffolding | `@backend-developer` agent |
| Frontend Angular CLI init + Tailwind setup | `@frontend-developer` agent |
| Project structure conventions | [.claude/rules/backend_coding.md §1](../../.claude/rules/backend_coding.md), [.claude/rules/frontend_coding.md §1.3](../../.claude/rules/frontend_coding.md) |
| Entity / migration conventions | [.claude/rules/backend_coding.md §4, §13](../../.claude/rules/backend_coding.md) |
| Version baselines | [.claude/rules/upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md) |
| Secrets handling for `.env.example` | [.claude/rules/security.md §1](../../.claude/rules/security.md) |
| Compile / build verification | `Skill("backend-verify")`, `Skill("frontend-verify")` |
| Final review | `Skill("code-review")` |
| Branch + PR | `Skill("create-merge-request")` |

> **Why not `Skill("backend-create-rest-api")`?** That skill scaffolds an end-to-end vertical slice (migration → entity → repo → DTO → service → resource → tests). This plan is intentionally entities-only; service/resource layers land in subsequent feature plans where the skill *is* invoked.

---

## 6. File Structure

```
DigitalWallet/
├── docker-compose.yml                                    # NEW
├── .env.example                                          # NEW
├── backend/                                              # NEW
│   ├── README.md                                         # NEW
│   ├── pom.xml                                           # NEW
│   ├── mvnw, mvnw.cmd, .mvn/                             # NEW (Quarkus Maven wrapper)
│   └── src/
│       ├── main/
│       │   ├── java/com/digitalwallet/
│       │   │   ├── wallet/
│       │   │   │   ├── api/package-info.java
│       │   │   │   ├── service/package-info.java
│       │   │   │   └── persistence/
│       │   │   │       ├── Account.java                  # ENTITY
│       │   │   │       └── Wallet.java                   # ENTITY
│       │   │   ├── transaction/
│       │   │   │   ├── api/package-info.java
│       │   │   │   ├── service/package-info.java
│       │   │   │   ├── event/package-info.java
│       │   │   │   └── persistence/
│       │   │   │       └── TransactionHistory.java       # ENTITY
│       │   │   ├── fraud/
│       │   │   │   ├── consumer/package-info.java
│       │   │   │   ├── rule/package-info.java
│       │   │   │   └── publisher/package-info.java
│       │   │   ├── admin/
│       │   │   │   ├── api/package-info.java
│       │   │   │   └── ws/package-info.java
│       │   │   └── shared/
│       │   │       ├── idempotency/package-info.java
│       │   │       └── config/package-info.java
│       │   └── resources/
│       │       ├── application.properties                # Quarkus config
│       │       └── db/migration/
│       │           ├── V1__create_accounts.sql
│       │           ├── V2__create_wallets.sql
│       │           └── V3__create_transaction_history.sql
│       └── test/java/com/digitalwallet/.gitkeep
└── frontend/                                             # NEW (via ng new)
    ├── angular.json
    ├── package.json
    ├── tailwind.config.js
    ├── postcss.config.js
    └── src/app/
        ├── core/.gitkeep
        ├── features/
        │   ├── wallet/.gitkeep
        │   └── admin/.gitkeep
        └── shared/.gitkeep
```

---

## 7. Files to Modify / Create

| Module | Path | Action | Layer |
|---|---|---|---|
| repo | `docker-compose.yml` | Create | infra |
| repo | `.env.example` | Create | config |
| repo | `.gitignore` (append) | Modify | config |
| backend | `backend/pom.xml` | Create | build |
| backend | `backend/mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Create (via Quarkus generator) | build |
| backend | `backend/README.md` | Create | docs |
| backend | `backend/src/main/resources/application.properties` | Create | config |
| backend | `backend/src/main/resources/db/migration/V1__create_accounts.sql` | Create | migration |
| backend | `backend/src/main/resources/db/migration/V2__create_wallets.sql` | Create | migration |
| backend | `backend/src/main/resources/db/migration/V3__create_transaction_history.sql` | Create | migration |
| backend | `backend/src/main/java/com/digitalwallet/wallet/persistence/Account.java` | Create | entity |
| backend | `backend/src/main/java/com/digitalwallet/wallet/persistence/Wallet.java` | Create | entity |
| backend | `backend/src/main/java/com/digitalwallet/transaction/persistence/TransactionHistory.java` | Create | entity |
| backend | `backend/src/main/java/com/digitalwallet/{wallet,transaction,fraud,admin,shared}/**/package-info.java` | Create (× ~14) | structure |
| frontend | `frontend/` (`ng new` output: `angular.json`, `package.json`, `tsconfig*.json`, `src/`, etc.) | Create | scaffold |
| frontend | `frontend/tailwind.config.js`, `postcss.config.js` | Create | config |
| frontend | `frontend/src/styles.css` | Modify (add Tailwind directives) | config |
| frontend | `frontend/src/app/{core,features/wallet,features/admin,shared}/.gitkeep` | Create | structure |

---

## 8. Progress Tracker

```
- [ ] Phase 1 — Repo-root infra: docker-compose.yml, .env.example, .gitignore  · @backend-developer
- [ ] Phase 2 — Backend Quarkus scaffold: pom.xml, mvnw, application.properties, README.md  · @backend-developer
- [ ] Phase 3 — Backend feature-package shell with package-info.java markers  · @backend-developer
- [ ] Phase 4 — Backend Flyway migrations V1–V3 (accounts, wallets, transaction_history)  · @backend-developer
- [ ] Phase 5 — Backend JPA entities (Account, Wallet, TransactionHistory)  · @backend-developer
- [ ] Phase 6 — Backend verify (./mvnw -DskipTests package)  · orchestrator · Skill("backend-verify")
- [ ] Phase 7 — Frontend init via ng new + Tailwind setup + directory shell  · @frontend-developer
- [ ] Phase 8 — Frontend verify (lint + build)  · orchestrator · Skill("frontend-verify")
- [ ] Phase 9 — Smoke test docker-compose stack (postgres reachable, Flyway clean target)  · orchestrator
- [ ] Phase 10 — Code review  · orchestrator · Skill("code-review")
```

Phase 1–6 must run sequentially (entities depend on package shell, which depends on Maven scaffold). Phase 7–8 (frontend) is independent of phases 1–6 and may be dispatched in parallel.

---

## 9. Acceptance Criteria

- [ ] `cd backend && ./mvnw -DskipTests clean package` produces `target/quarkus-app/quarkus-run.jar` (Quarkus fast-jar layout) without errors.
- [ ] `cd backend && ./mvnw quarkus:dev` boots the application against the running Compose stack and serves `/q/health` `200 OK`.
- [ ] Every package listed in [docs/architecture/README.md §3](../../docs/architecture/README.md) (`wallet/api`, `wallet/service`, `wallet/persistence`, `transaction/api`, `transaction/service`, `transaction/persistence`, `transaction/event`, `fraud/consumer`, `fraud/rule`, `fraud/publisher`, `admin/api`, `admin/ws`, `shared/idempotency`, `shared/config`) exists with at least a `package-info.java`.
- [ ] `Account.java`, `Wallet.java`, `TransactionHistory.java` exist under `com.digitalwallet.wallet.persistence` / `com.digitalwallet.transaction.persistence`, each annotated `@Entity` and pointing at its corresponding table name. None of them extend `PanacheEntityBase`.
- [ ] All money columns use `BigDecimal` + `numeric(19,4)`; all timestamps use `OffsetDateTime` + `timestamptz` ([backend_coding.md §4.3, §4.4](../../.claude/rules/backend_coding.md)).
- [ ] `wallets.balance` column has DB-level `CHECK (balance >= 0)`.
- [ ] `Wallet` carries an `@Version` field of type `long`.
- [ ] `quarkus.hibernate-orm.database.generation=validate` in `application.properties` ([backend_coding.md §13.3](../../.claude/rules/backend_coding.md)).
- [ ] `cd frontend && npx ng build` succeeds; `npx ng lint` reports no errors.
- [ ] Tailwind utility class (e.g., `class="bg-slate-100"`) renders on the default Angular landing page.
- [ ] `docker compose up -d postgres redis kafka zookeeper` boots all four services healthy. `psql` connects to postgres using `.env.example`-derived credentials. `redis-cli ping` returns `PONG`. `kafka-topics --list` succeeds.
- [ ] On `./mvnw quarkus:dev`, `quarkus-flyway` applies V1–V3 cleanly against the Compose Postgres.
- [ ] `.env.example` contains every variable in [docs/architecture/README.md §7](../../docs/architecture/README.md) and **no real secrets**.
- [ ] `.env`, `.env.local`, `*.pem`, `*.key` are present in `.gitignore` ([security.md §1.1](../../.claude/rules/security.md)).
- [ ] `backend/README.md` describes: prerequisites (Java 21, Docker), package layout, how to start Compose infra, how to run dev mode (`./mvnw quarkus:dev`), where Flyway migrations live, where entities live.

---

## 10. Security Considerations

| Section | Concern | How this plan addresses it |
|---|---|---|
| [§1.1](../../.claude/rules/security.md) No committed secrets | `.env`, `.env.local`, `*.pem`, `*.key`, `id_rsa*`, `*.p12`, `credentials.json` must be in `.gitignore`. | `.env.example` is committed with placeholder values only. `.gitignore` (top-level) gains `.env`, `.env.*`, `!.env.example`, `*.pem`, `*.key`, `*.p12`, `*.pfx`, `id_rsa*`. |
| [§1.2](../../.claude/rules/security.md) Env-var loading | Backend uses Microprofile Config `@ConfigProperty`; frontend uses `environment.*.ts`. | Plan sets up the env-var contract in `.env.example`; `application.properties` references env vars (`${QUARKUS_DATASOURCE_JDBC_URL}` etc.). No `@ConfigProperty` use yet (no service code in this scaffold). |
| [§1.3](../../.claude/rules/security.md) Frontend env rules | `environment.*.ts` ships to the browser; no server secrets. | `ng new` generates default `environment.ts`; this plan does not populate any secrets there. |
| [§1.4](../../.claude/rules/security.md) Log scrubbing | N/A — no logging code in this plan. | — |
| [§2](../../.claude/rules/security.md) Authentication | Auth scheme unspecified ([docs/architecture/README.md §6](../../docs/architecture/README.md)). | Not introduced in this plan. Endpoints will be added gated behind `@RolesAllowed` once an ADR commits. |
| [§3.1](../../.claude/rules/security.md) Default-deny posture | Every JAX-RS resource without `@RolesAllowed` is unreachable. | N/A — no resources in this plan. Future feature plans must enforce. |
| [§4](../../.claude/rules/security.md) Input validation | Bean Validation at JAX-RS boundary. | N/A — no resources or DTOs in this plan. |
| [§5.2](../../.claude/rules/security.md) CORS | Wildcard origins forbidden in prod. | Compose binds infra to `127.0.0.1` only; backend CORS is not configured (no resources yet). |
| [§7.3](../../.claude/rules/security.md) Idempotency-key handling | Treat as request secret. | N/A in this plan — idempotency persistence stays in Redis ([ADR-0005](../../docs/decisions/0005-idempotency-key-header.md)) and is not scaffolded here. |
| [§9.1](../../.claude/rules/security.md) Dependency scanning | CI scanner on every PR. | `<!-- not-yet-adopted -->` per the rules file; this plan adds a backlog row in `upgrade-policy.md §6` (or notes the gap in PR description). |

---

## 11. Testing Strategy

- **Unit tests planned:** none — entities have no logic; per [testing.md §5](../../.claude/rules/testing.md), trivial getters/setters and constructor wiring are excluded from coverage.
- **Integration tests planned:** none in this plan. A Testcontainers Postgres + Flyway smoke test (via `@QuarkusTest` + `@QuarkusTestResource`) is appropriate for the next plan (when the first repository class lands).
- **Frontend specs:** none — no components yet. Default Angular `app.component.spec.ts` from `ng new` remains as-is.
- **Coverage floor:** [NFR4](../../docs/testing/README.md) 80% service-layer floor does not apply yet (no service layer). Floor activates with the next plan.
- **Verification commands:**
  - `cd backend && ./mvnw -DskipTests clean package` — must succeed.
  - `cd frontend && npx ng build && npx ng lint` — must succeed.
  - `docker compose up -d` — all services healthy; ports reachable on `127.0.0.1`.

---

## 12. Reference Files

- [README.md](../../README.md) — project spec; FR / NFR table.
- [CLAUDE.md](../../CLAUDE.md) — orientation for Claude in this repo; restates the stack mandate.
- [docs/architecture/README.md](../../docs/architecture/README.md) — backend (§3) and frontend (§4) layering, env-var table (§7), deployment topology (§8).
- [docs/database/README.md](../../docs/database/README.md) — ERD (§1), table list (§2), conventions (§3).
- [docs/database/migrations.md](../../docs/database/migrations.md) — migration log (currently empty).
- [docs/decisions/0001-quarkus-over-spring-boot.md](../../docs/decisions/0001-quarkus-over-spring-boot.md) — runtime constraint.
- [docs/decisions/0002-postgresql-with-flyway.md](../../docs/decisions/0002-postgresql-with-flyway.md) — Flyway as the migration tool.
- [.claude/rules/backend_coding.md](../../.claude/rules/backend_coding.md) — entity (§4), repository (§5), migration (§13) rules.
- [.claude/rules/frontend_coding.md](../../.claude/rules/frontend_coding.md) — directory layout (§1.3), Tailwind (§6).
- [.claude/rules/upgrade-policy.md](../../.claude/rules/upgrade-policy.md) — version baselines (§1).
- [.claude/rules/security.md](../../.claude/rules/security.md) — secrets and env handling (§1).

---

## 13. Risks & Dependencies

| Risk | Impact | Mitigation |
|---|---|---|
| Quarkus BOM version drift between `pom.xml` and `upgrade-policy.md §1`. | Reproducibility issue; surprise upgrades. | Pin the BOM version in `pom.xml` and commit the same version into `upgrade-policy.md §1` in the same PR. |
| `(verify)` rows in [upgrade-policy.md §1](../../.claude/rules/upgrade-policy.md) (Postgres, Redis, Kafka, Mockito) get pinned ad-hoc in `pom.xml` and `docker-compose.yml`. | Drift between rule file and committed versions. | This plan's PR replaces `(verify)` cells with concrete pinned versions in `upgrade-policy.md §1`. |
| Open ADR-0006 (Outbox vs publish-after-commit) means we may need an `outbox_events` table later. | Future migration `V<n>__create_outbox_events.sql`. | Out of scope here — explicit in §2 Scope. Forward-only Flyway accommodates it cleanly. |
| Compose `kafka` + `zookeeper` config drift across environments. | Local-only stack — production stack is out of scope. | Stack is scoped to `local` profile per [security.md §5.2](../../.claude/rules/security.md). |
| `ng new` is interactive in some terminals. | Phase 7 stalls. | Use `ng new` non-interactive flags: `--standalone --routing --style=css --skip-git --package-manager=npm --defaults`. |
| Dependency on docker daemon being up. | Phase 9 fails locally if user has Docker stopped. | Plan acceptance allows skipping Phase 9 with a note; CI will catch it. |
| Quarkus dev-services may auto-start Postgres / Kafka containers if env vars are missing. | Confusing dev-mode behaviour vs. the explicit Compose stack. | Disable dev-services explicitly: `quarkus.devservices.enabled=false` in `application.properties` so the Compose stack is the single source of truth. |

### Cross-module contract

- Backend entity field names (`balance`, `created_at`, `idempotency_key`, etc.) become the wire-format basis when DTOs land in the next plan. No frontend code currently consumes them; first cross-module contract risk activates in the deposit/withdraw plan.
