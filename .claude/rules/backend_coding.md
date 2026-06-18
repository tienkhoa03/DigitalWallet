# Backend coding rules

This file is the coding contract for every Quarkus / Java module under `backend/`. The `code-review` skill (step 4 onwards) checks pull requests against the sections below. Every rule is enforceable: a "MUST" failure is a release blocker; a "MUST NOT" describes a defect.

> **Status:** the codebase is not yet scaffolded. Every rule cites either `docs/` or a section of [../../project-info.md](../../project-info.md). Code examples use module names from [../../docs/architecture/README.md](../../docs/architecture/README.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

## 1. Project structure

The target module tree is defined in [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering); the organising principle (per-module hexagonal — ports & adapters) is stated there. Each feature module (`account`, `wallet`, `fraud`, `pfm`, `advisor`, `dashboard`) is a self-contained hexagon and `shared/` is the cross-cutting kernel.

**Per-module layout.** Inside each module under `backend/src/main/java/com/digitalwallet/<module>/`:

- `domain/` — framework-free entities, value objects, domain services, and rules (`domain/model/`). No JPA / Jakarta annotations here.
- `application/port/in/` — inbound ports: use-case interfaces (`CreateAccountUseCase`, `DepositUseCase`).
- `application/port/out/` — outbound ports: SPIs the use case calls (`LoadAccountPort`, `SaveAccountPort`, `PublishEventPort`, `WalletLockPort`, `FraudCounterPort`, `IdempotencyPort`, …).
- `application/service/` — use-case **implementations** = application services (`@ApplicationScoped`, `@Transactional`; RBAC + domain invariants live here).
- `adapter/in/web/` — inbound REST adapter: JAX-RS resources + request/response DTOs + mappers.
- `adapter/in/messaging/` — inbound Kafka adapter: `@Incoming` consumers.
- `adapter/out/persistence/` — outbound JPA adapter: `<Name>Entity` + Panache repository + `<Name>PersistenceAdapter`.
- `adapter/out/redis/` — outbound Redis adapter (locks, sliding-window counters, idempotency, read-model).
- `adapter/out/messaging/` — outbound Kafka adapter (the outbox poller is the sole producer; it lives in `shared/`).
- `adapter/out/llm/` — outbound LLM adapter (advisor module only).

**The dependency rule (hexagonal core invariant).** Dependencies point INWARD only: `adapter` → `application` → `domain`.

- `domain/` depends on nothing; `application/` depends only on `domain/` and its own ports; `adapter/` depends on `application/` (ports) and `domain/`.
- Frameworks (JAX-RS, Hibernate/Panache, SmallRye Kafka, Redis client, MicroProfile Config, SmallRye Fault Tolerance) appear ONLY in `adapter/` packages (and wiring/config). `domain/` and `application/` are framework-free.
- Application services depend on **outbound ports** (`port/out` interfaces), never on concrete adapters. The outbound adapter `implements` the port.
- Inbound web/messaging adapters depend on **inbound ports** (`port/in` use-case interfaces), never on application service classes directly where an interface exists.

**Cross-module rules:**

- A module's `adapter/` MUST NOT import another module's `domain/`, `application/`, or `adapter/`. Cross-module collaboration goes through Kafka topics (outbound→inbound messaging adapters, [../../docs/architecture/README.md §5](../../docs/architecture/README.md#5-how-modules-connect)) or a port exposed in `shared/`.
- The `pfm/` module MUST NOT have an outbound persistence adapter / port onto `transaction`, `wallet`, or `outbox_event`. PFM state lives in Redis and the materialised view per NFR6 ([../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md), `Cross-cutting → Rule (no direct ledger UPDATE)`).
- `adapter/in/messaging` consumers invoke their own module's inbound ports (use cases) directly; they MUST NOT call JAX-RS resources.

**Shared kernel.** Cross-cutting helpers (money type, idempotency store, outbox poller, security, lock helper, rate-limit middleware, audit-log writer) MUST live in `backend/shared/` and be consumed by feature hexagons via ports — never copied. `shared/` holds the `DomainException` hierarchy + JAX-RS `ExceptionMapper`, security (JWT, Argon2, roles, clock), validation (custom constraints), the shared-kernel money type, and the cross-cutting outbound adapters / infrastructure: idempotency store, transactional-outbox poller, rate-limit middleware, and the Redis distributed-lock helper.

## 2. Inbound web adapter (REST)

The `api/` / controller layer is the **inbound web adapter** (`adapter/in/web`): JAX-RS resources that accept HTTP, validate the payload, delegate to an inbound port (use case), and return DTOs.

- **Framework:** JAX-RS via RESTEasy Reactive, mandated by [../../project-info.md §4.1](../../project-info.md#41-backend).
- **Paths:** the canonical endpoint catalog lives in [../../docs/api/README.md](../../docs/api/README.md); MUST match it. New endpoints update the catalog in the same PR.
- **Path constants:** every resource MUST expose its base path as a `public static final String` and the JAX-RS `@Path` MUST reference it. Hard-coded path literals scattered across resources are a defect.
- **Response objects:** resources MUST return DTOs (see §6) or `Response`/`RestResponse` wrappers — Never expose JPA entities or domain model types over the wire.
- **Required per-endpoint headers / middleware:**
  - Mutating money endpoints (deposit, withdraw, transfer) MUST require an `Idempotency-Key` header (NFR3, [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.2/FR1.3).
  - `POST /transfers` MUST pass through the Redis token-bucket rate limiter (10/min/user, [../../project-info.md §8](../../project-info.md#8-security-baseline)).
  - `POST /advisor/*` MUST pass through the Redis token-bucket rate limiter (5/hour/user).
  - The inbound web adapter MUST NOT publish to Kafka directly — publishing is the outbox poller's job (NFR2/NFR5, [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) FR2.3).
- **RBAC:** adapter-level `@RolesAllowed` is a hint; the authoritative check MUST also be at the application service / use-case impl (see §3 and [security.md §3](security.md#3-authorization)).
- **Async request-reply:** advisor endpoints MUST return `HTTP 202` with a `request_id` and deliver the final payload over WebSocket (NFR8, [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) FR6.1).

Example shape — lives in `wallet/adapter/in/web` and depends on the inbound port (`DepositUseCase`), not the application service class `<!-- not-yet-adopted -->`:

```java
@Path(WalletPaths.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {

    public static final class WalletPaths {
        public static final String BASE = "/wallets";
        public static final String DEPOSIT = "/{walletId}/deposits";
    }

    private final DepositUseCase deposit; // inbound port (application/port/in)

    public WalletResource(DepositUseCase deposit) { // constructor injection only (§13)
        this.deposit = deposit;
    }

    @POST
    @Path(WalletPaths.DEPOSIT)
    @RolesAllowed("USER")
    public RestResponse<DepositResponse> deposit(
            @PathParam("walletId") UUID walletId,
            @HeaderParam("Idempotency-Key") @NotNull UUID idempotencyKey,
            @Valid DepositRequest request) {
        return RestResponse.ok(deposit.deposit(walletId, idempotencyKey, request));
    }
}
```

## 3. Application service / use case

The business layer is the **application service** (`application/service`): the use-case implementation that `implements` an inbound port (`application/port/in`) and orchestrates the domain plus outbound ports.

- **Interface vs. impl:** the inbound port (use-case interface) lives in `application/port/in`; its implementation is the application service in `application/service`. Do not invent extra `FooService` + `FooServiceImpl` pairs beyond the port/impl split — the inbound port *is* the interface.
- **Naming:** `<Verb><Noun>UseCase` for the inbound port (`DepositUseCase`, `CreateAccountUseCase`); the implementing application service is `<Noun>Service` or `<Verb><Noun>Service` only when behaviour is verb-shaped (e.g. `OutboxPublisherService`). Methods MUST be verb phrases (`deposit`, `transfer`, `recordFraudAlert`).
- **Outbound ports, not adapters:** application services depend on **outbound ports** (`application/port/out` interfaces) — `LoadAccountPort`, `SaveAccountPort`, `PublishEventPort`, `WalletLockPort`, `FraudCounterPort`, `IdempotencyPort` — never on the concrete outbound adapter. The outbound adapter in `adapter/out/*` `implements` the port; the use case never imports it.
- **Transaction boundary:** `@Transactional` MUST be applied at the application service (use-case impl) method, NOT at the inbound web adapter or the outbound persistence adapter. A method that both reads and writes the ledger MUST be `@Transactional` (default `REQUIRED`). Read-only flows SHOULD declare `@Transactional(TxType.SUPPORTS)` or omit the annotation entirely.
- **Hybrid concurrency (NFR1):** every wallet mutation MUST follow the exact order — (1) acquire the Redis distributed lock keyed on `wallet_id` with a short TTL via the lock port backed by the helper in `shared/`; (2) open the `@Transactional` boundary; (3) read the wallet row with `LockModeType.PESSIMISTIC_WRITE` (via the outbound persistence port); (4) write the ledger row + outbox row; (5) commit; (6) release the Redis lock in a `finally`. The ordering is UNCHANGED — it lives in the application service. See [../../docs/business-rules/README.md NFR1](../../docs/business-rules/README.md#nfr-enforcement-matrix) and [../../docs/decisions/0003-concurrency-strategy.md](../../docs/decisions/0003-concurrency-strategy.md).
- **Synchronous fraud pre-check (NFR9):** every wallet mutation MUST run the bounded fraud pre-check (velocity FR2.1 + volume FR2.2 Redis sliding-window lookups + `account.fraud_status` read FR2.4) inside the application service **before** the Redis wallet lock is acquired. A breach rejects with the typed exception in §8 (`fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended` — error key preserved for API back-compat) and writes one `transaction.blocked` outbox event in a short `@Transactional` boundary — no ledger row, no wallet lock. *(MVP defers the `audit_log` row originally specified for blocks — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)* See [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) and [../../docs/decisions/0010-fraud-enforcement-model.md](../../docs/decisions/0010-fraud-enforcement-model.md).
- **Validation flow:**
  - Bean Validation on DTOs runs at the inbound web adapter boundary (annotation-driven).
  - Cross-field and domain invariants (insufficient funds, FX missing, currency mismatch, recipient identity) run inside the application service / domain.
  - DB constraints are the last line (unique `(account_id, label)` on `wallet`, unique `(account_id, month)` on `budget`, idempotency-key uniqueness) — surfaced through the exception mapper (§8).
- **RBAC MUST run in the application service (use case)**, not only at the inbound web adapter. The use case receives a `SecurityIdentity` or equivalent principal and rejects unauthorised callers with the typed exception in §8. Source: [../../project-info.md §8](../../project-info.md#8-security-baseline) and [security.md §3](security.md#3-authorization).
- **What MUST NOT happen on the request thread:**
  - Kafka publishing (§15, NFR2/NFR5).
  - LLM calls (NFR8).
  - Cross-event fraud analysis, alert fan-out (FR2.5), the suspension-policy decision (FR2.4), PFM aggregation, dashboard aggregation — these are inbound-messaging-adapter (Kafka consumer) concerns ([../../docs/business-rules/README.md NFR5](../../docs/business-rules/README.md#nfr-enforcement-matrix)). The bounded fraud pre-check (FR2.1 / FR2.2 Redis counters + `account.fraud_status` read) is the only fraud step permitted inline (NFR9; see the §3 hybrid-concurrency / pre-check rules).
  - Blocking I/O outside the configured worker / virtual-thread executor.

## 4. Data models / entities

The domain model is framework-free and lives in `domain/model`; the JPA `<Name>Entity` is an adapter concern in `adapter/out/persistence` and is mapped to/from domain types (see §5, §7). Domain model ≠ JPA entity.

- **No persistence annotations on the domain:** `domain/model` types MUST NOT carry JPA / `jakarta.persistence` annotations. JPA `<Name>Entity` classes live in `adapter/out/persistence`.
- **PK strategy:** UUID (`uuid` Postgres column). Generated client-side, time-orderable variant preferred where ordering is useful (e.g. `uuidv7`). See [../../docs/database/README.md](../../docs/database/README.md#naming-conventions).
- **Date/time handling:** Java `Instant` or `OffsetDateTime` mapped to `timestamptz`. All timestamps are UTC ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)). MUST NOT use `Date`, `Calendar`, or `LocalDateTime` for stored timestamps.
- **Money columns:** `BigDecimal` in Java, `numeric(19,4)` in Postgres ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)). MUST NOT use `double` or `float` for money under any circumstance.
- **Event time:** `event_timestamp` on `transaction` is the event time carried into Kafka (NFR7, [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) Cross-cutting). Inbound-messaging-adapter (consumer) code MUST use the event time from the Kafka payload, not `Instant.now()`.
- **Currency code:** ISO 4217, stored as `varchar(3)`. The domain side MAY model it as a value object or a typed enum; the wire form is the three-letter string.
- **Relationships:** on the JPA entity, `@ManyToOne(fetch = LAZY)` is the default. `EAGER` fetch is forbidden on collection associations. Use explicit `JOIN FETCH` or a projection DTO when you need eager-like loading; this is the primary N+1 mitigation.
- **N+1 avoidance:** every multi-row read flow that crosses an association MUST be covered by an integration test that asserts the SQL count via Hibernate Statistics (or equivalent) `<!-- not-yet-adopted -->`. The list of indexed columns lives in [../../docs/database/README.md](../../docs/database/README.md#naming-conventions).

## 5. Data access

Data access is the **outbound persistence adapter** (`adapter/out/persistence`): a class that `implements` an outbound port (`application/port/out`, e.g. `LoadWalletPort` / `SaveWalletPort`) and owns the Panache repository and the `<Name>Entity` ↔ domain mapping.

- **Repository style:** Hibernate ORM with Panache, used *inside* the outbound persistence adapter. Prefer the Panache Repository pattern (`PanacheRepositoryBase<T, UUID>`) over Active Record (`PanacheEntityBase` on the entity) — keeps entities free of static methods and makes mocking trivial in unit tests. Source: [../../project-info.md §4.1](../../project-info.md#41-backend).
- **Locking policy:** wallet reads inside the money path MUST use `LockModeType.PESSIMISTIC_WRITE`. The Redis distributed lock MUST be acquired first (NFR1). Acquisition order is fixed: **Redis lock → DB row lock → outbox write → commit → Redis lock release**. See [../../docs/business-rules/README.md NFR1](../../docs/business-rules/README.md#nfr-enforcement-matrix) and [../../docs/decisions/0003-concurrency-strategy.md](../../docs/decisions/0003-concurrency-strategy.md).
- **Query building:** prefer Panache `find` / `list` with named parameters, or JPQL with named parameters. MUST NOT concatenate string fragments containing untrusted input into queries (see [security.md §4](security.md#4-input-validation--injection)).
- **Native SQL** is permitted for materialised-view refresh, the outbox poller, and tight ledger reads where JPQL is expressively insufficient. Native SQL containing user-supplied input MUST use bound parameters.
- **Return types:** the outbound port and its adapter return `Optional<T>` for single-row lookups, `List<T>` for multi-row, `Stream<T>` only when the caller is responsible for closing it within the same `@Transactional` scope. Never return `null`.
- **Pagination:** every list query that can grow unbounded MUST accept `(int page, int pageSize)` and apply a server-side cap (see §10).
- **Read flows are not lock flows:** statement queries (FR1.4) MUST NOT acquire write locks. The wallet statement-reader adapter runs without `PESSIMISTIC_WRITE` ([../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.4).

## 6. DTOs

Request/response DTOs are an inbound-web-adapter concern and live in `adapter/in/web`. The wire boundary MUST NOT expose JPA entities or unmapped domain types.

- **Naming:**
  - Request DTOs: `<Action><Noun>Request` — `DepositRequest`, `CreateBudgetRequest`, `OpenWalletRequest`.
  - Response DTOs: `<Noun>Response` — `WalletResponse`, `TransferResponse`, `BudgetResponse`.
  - Event payloads: `<Noun>Event` — `TransactionEvent`, `FraudAlertEvent`.
- **Shape:** Java `record` is the default. Use a class only when the DTO needs custom validation logic that cannot be expressed via Bean Validation, or when a record forbids a feature you genuinely need (very rare).
- **Never expose entities or domain types unmapped.** A JAX-RS resource MUST NOT return a JPA entity or a raw domain model object directly. A use case that fans out an entity over the API surface is a defect — the inbound web adapter maps the domain result into a response DTO. Source: [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide).
- **No bidirectional shapes.** Request DTOs and Response DTOs are separate types even when fields overlap. Reuse via shared field-level records (`MoneyAmount(BigDecimal value, String currencyCode)`) is encouraged.

## 7. Object mapping

- **Library:** hand-written mappers in the adapter that owns the boundary — DTO ↔ domain in `adapter/in/web`, domain ↔ `<Name>Entity` in `adapter/out/persistence` — or a dedicated `mapping/` package within the adapter. MapStruct is allowed when it earns its keep on a DTO with ≥ 5 fields. Library choice is per module; once a module picks a library, MUST be consistent.
- **Domain ↔ entity mapping:** the outbound persistence adapter MUST map domain model types to/from `<Name>Entity` at its boundary (§1, §4). Domain types never leak `jakarta.persistence` state; the JPA entity never travels inward past the port.
- **Null-value strategy for partial updates:** `PATCH` endpoints MUST distinguish "field omitted" from "field set to null". Use `Optional<T>` fields on the request DTO (or a wrapper sentinel) — never interpret a missing JSON property as "set to null" silently. Source: aligned with [../../docs/api/README.md](../../docs/api/README.md) which lists `PATCH /budgets/{budgetId}/buckets/{bucketId}/threshold`.

## 8. Exception handling

- **Custom base:** every domain exception MUST extend `shared.DomainException`, which carries an `errorKey` (dot-separated identifier) and a human message ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)).
- **Global mapper:** a single JAX-RS `ExceptionMapper<DomainException>` in `shared/` MUST own the HTTP envelope. Per-resource `try`/`catch` blocks that build JSON manually are a defect.
- **Error envelope:** the canonical shape is defined in [../../docs/api/README.md](../../docs/api/README.md#error-response-shape).
- **Status code mapping table** (authoritative; inbound web adapters and application services raise the typed exception, the mapper sets the status):

  | `errorKey` family | HTTP status | Exception class (target) |
  |---|---|---|
  | `validation.*` | 400 | `ValidationException` |
  | `idempotency.key_required` | 400 | `IdempotencyKeyRequiredException` |
  | `auth.invalid_credentials` | 401 | `AuthInvalidCredentialsException` |
  | `auth.forbidden`, `account.suspended` | 403 | `AuthForbiddenException` / `AccountSuspendedException` (FR2.4) |
  | `wallet.duplicate_label`, `budget.duplicate_month`, `idempotency.replay_conflict`, `wallet.locked` | 409 | `ConflictException` (subclassed per domain) |
  | `wallet.insufficient_funds`, `transfer.fx_rate_missing`, `transfer.recipient_not_found`, `transfer.same_wallet`, `wallet.currency_mismatch`, `advisor.month_not_ready`, `validation.invalid_amount`, `fraud.velocity_exceeded`, `fraud.volume_exceeded` | 422 | `BusinessRuleException` (subclassed per domain — fraud subclasses for FR2.1 / FR2.2) |
  | `ratelimit.exceeded` | 429 (`Retry-After` header required) | `RateLimitException` |
  | `advisor.circuit_open` | 503 | `CircuitOpenException` |

  `errorKey` values referenced above come from [../../docs/api/README.md](../../docs/api/README.md) and [../../docs/business-rules/](../../docs/business-rules/) and are part of the public contract — clients branch on them. *(MVP defers `audit.write_failed` together with the `audit_log` table — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)*
- **Hibernate Validator failures** MUST be normalised onto the same envelope by the mapper with `errorKey: "validation.invalid_payload"`.
- **MUST NOT** swallow exceptions silently or log-and-return — every recoverable failure surfaces a typed exception; every unrecoverable failure logs at `ERROR` with the stack and re-throws.

## 9. Security configuration

- The cross-cutting security floor lives in [security.md](security.md). Every backend module MUST conform to it.
- **Method-level authorization:** in addition to adapter-side `@RolesAllowed`, application services (use cases) MUST re-check the caller's role and ownership before mutating state ([security.md §3](security.md#3-authorization)).
- **CORS posture:** the application MUST start with a CORS allow-list (no `*` origin in any non-dev profile). Source: [security.md §5](security.md#5-transport--cors).
- **WebSocket handshake:** WebSocket upgrades MUST validate the JWT before accepting the connection ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) RBAC, [../../docs/business-rules/pfm-notifications-rules.md](../../docs/business-rules/pfm-notifications-rules.md) Cross-cutting).

## 10. Pagination & sort safety

This section is CRITICAL. Unsafe sort parameters are the most common JPQL-injection path. The whitelist lives in the inbound web adapter or the application service (use case); the unsafe interpolation NEVER reaches the outbound persistence adapter.

- **Pagination parameters:** `page` (zero-based) and `pageSize`. Defaults: `page=0`, `pageSize=20`.
- **Caps:** `pageSize` MUST be capped server-side at `100`. Requests above the cap MUST be silently clamped (do not error — the contract is "best-effort up to 100").
- **Sort whitelist:** every endpoint that accepts a `sort` parameter MUST validate it against an explicit whitelist defined in the inbound web adapter or the application service. Unknown sort keys MUST raise `validation.invalid_payload`.
- **NEVER** interpolate a `sort` parameter into a JPQL `ORDER BY` clause. Map the whitelisted key to a `Sort` instance or a fixed JPQL fragment.

Example `<!-- not-yet-adopted -->`:

```java
private static final Map<String, Sort> ALLOWED_SORTS = Map.of(
        "createdAt", Sort.by("created_at"),
        "amount",    Sort.by("amount"));

private Sort resolveSort(String key) {
    Sort sort = ALLOWED_SORTS.get(key);
    if (sort == null) throw new ValidationException("validation.invalid_payload", "Unknown sort key");
    return sort;
}
```

## 11. Logging

- **Library:** SLF4J via JBoss Logging (Quarkus default) ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)).
- **Placeholder syntax:** parameterised `{}` placeholders. MUST NOT use string concatenation in log calls (`log.info("user " + email)`).
- **Levels:**
  - `INFO` — business events (`transfer.commit`, `wallet.open`, consumer offset committed).
  - `WARN` — recoverable degradations (Redis lock missed; LLM call retried; FX rate expired between preview and commit).
  - `ERROR` — unrecovered failures requiring human attention (outbox poller exception; consumer poison message routed to DLQ).
  - `DEBUG` — traceable execution detail used in dev mode and turned off in prod by default.
- **Forbidden content in logs:**
  - Email, full name, password, JWT, account number, wallet balance, transaction amount per row.
  - Full `Idempotency-Key` value (log a salted hash or first 8 chars only — see [security.md §7](security.md#7-sensitive-data-exposure)).
  - LLM prompt or response bodies (they contain user spending data).
  - Postgres connection strings or any secret value.
- **No PII in logs** is a SOC 2 obligation ([../../project-info.md §8](../../project-info.md#8-security-baseline), [../../docs/business-rules/README.md](../../docs/business-rules/README.md) Cross-cutting security rules).

## 12. Validation

- **Library:** Hibernate Validator (Bean Validation) ([../../project-info.md §4.1](../../project-info.md#41-backend)).
- **Creation vs. update DTOs:** separate types. A `CreateBudgetRequest` validates "required fields present"; a `PatchBudgetRequest` validates "fields are well-formed when present". MUST NOT reuse the same DTO for both.
- **Domain ranges** baked into annotations: amounts `> 0` (`@DecimalMin(value = "0", inclusive = false)`), `threshold_percent` in `[1, 100]` ([../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) FR4.3).
- **Custom validators** for ISO 4217 currency codes (`@CurrencyCode`) and ISO-8601 month strings (`@YearMonthString`) live in `shared/validation/`.

## 13. Database migrations

- **Tool & policy:** Flyway, versioned SQL only, forward-only. See [../../docs/database/migrations.md](../../docs/database/migrations.md).
- **Naming:** `V<version>__<short_snake_case_description>.sql` ([../../docs/database/migrations.md](../../docs/database/migrations.md#naming-convention)).
- **Entity + migration in the same PR.** A JPA entity (in `adapter/out/persistence`) introduced or modified without a corresponding Flyway migration is a defect. The migration MUST be applied before the application code that depends on it merges.
- **No auto-DDL.** `quarkus.hibernate-orm.database.generation` MUST be `none` in every profile ([../../docs/database/migrations.md](../../docs/database/migrations.md#tool)).
- **Reference seeds** (currencies, FX rates, default categories) live in Flyway migrations; demo / fixture data lives under `backend/postgres/init/` (invoked by the Postgres container's `docker-entrypoint-initdb.d` hook in `backend/docker-compose.yml`) ([../../docs/database/migrations.md](../../docs/database/migrations.md#seed-data)).
- **Drop / rename** of columns or tables follows the staged pattern referenced in [../../docs/database/migrations.md](../../docs/database/migrations.md#conventions-for-the-migration-content): ship the read-side fallback first, then the rename in a subsequent release.

## 14. Unit tests

- See [testing.md §2](testing.md#2-backend-testing) for the full backend testing contract.
- **Framework:** JUnit 5 + Mockito ([../../project-info.md §4.5](../../project-info.md#45-testing--quality)).
- **Coverage floor:** ≥ 80% line coverage on the application service layer (the use-case implementations) (NFR4, [../../docs/business-rules/README.md](../../docs/business-rules/README.md#nfr-enforcement-matrix)). The JaCoCo gate targets `com/digitalwallet/*/application/service/**`. *(The `pom.xml` include pattern moves with the Java code restructure, a separate step; until then it still reads `com/digitalwallet/*/service/**`.)* CI fails below the threshold.
- **Naming:** `methodUnderTest_givenCondition_thenExpectedOutcome` or behaviour-describing snake form (`transfer_with_replayed_idempotency_key_returns_original_outcome` — see [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline)).
- **Mocking matrix at a glance** (full matrix in [testing.md §2](testing.md#2-backend-testing)) — unit tests mock the outbound ports, integration tests use the real adapter:

  | Collaborator | Unit tests | Integration tests |
  |---|---|---|
  | Postgres | Mock the outbound persistence port | Testcontainers Postgres 16 |
  | Kafka producer | Mock the emitter | Testcontainers Kafka |
  | Kafka consumer | Drive the inbound messaging adapter method directly | Testcontainers Kafka |
  | Redis | Mock the port / helper | Testcontainers Redis 7 |
  | LLM client | Mock | Wiremock or recorded fixture |
  | Time / clock | Inject a `Clock`; never call `Instant.now()` directly in application-service code | Same |

## 15. Messaging (Kafka)

Kafka consumers are **inbound messaging adapters** (`adapter/in/messaging`); the outbox poller is the sole **outbound** producer and lives in `shared/`.

- **Client:** SmallRye Reactive Messaging (Kafka extension) ([../../project-info.md §4.1](../../project-info.md#41-backend)).
- **Channel naming:** application channel names mirror the Kafka topic name, hyphenated: `transaction-events`, `fraud-alerts`, `pfm-threshold-alerts`. Advisor topics are TBD per [../../project-info.md §16](../../project-info.md#16-open-questions-to-answer-before-bootstrapping) item 1; once decided they MUST follow the same convention.
- **Producer pattern:** the request thread MUST NOT call `@Channel("...").send(...)`. The only producer is the outbox poller in `shared/`, driven by `@Scheduled` ([../../docs/decisions/0005-outbox-publisher.md](../../docs/decisions/0005-outbox-publisher.md), [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) FR2.3). All other modules append to the outbox table.
- **Consumer pattern:** `@Incoming` methods (the inbound messaging adapter) MUST be idempotent (de-duplicate on outbox-event `id` — at-least-once delivery is the contract per NFR2) and MUST delegate to their own module's inbound port (use case), never to a JAX-RS resource. They MUST run on a consumer-managed executor, not on JAX-RS threads ([../../docs/business-rules/README.md NFR5](../../docs/business-rules/README.md#nfr-enforcement-matrix)).
- **Event-time correctness:** PFM and any downstream aggregator MUST use `event_timestamp` from the payload, not consumer wall-clock (NFR7, [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) Cross-cutting).
- **Failure handling:** a consumer that throws on a record MUST mark the record as failed (negative ack) and route to the per-topic DLQ (`<topic>.DLQ`) after a bounded retry count. The DLQ MUST be drained by a separate operational tool — Never silently swallowed.
- **Outbox poller:** drains rows in `created_at ASC` order; on publish success, sets `published_at` (see [../../docs/database/README.md](../../docs/database/README.md) `outbox_event`).

## 16. WebSockets

WebSocket endpoints are inbound adapters (`adapter/in/web`, alongside the REST resources); the JWT/`Origin` auth rules are unchanged.

- **Endpoint patterns** are defined in [../../docs/api/README.md](../../docs/api/README.md):
  - `WS /admin/ws/alerts` — fraud alert fan-out, `ADMIN` only (MVP defers `FRAUD_ANALYST` — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md)).
  - `WS /admin/ws/metrics` — live metrics push, `ADMIN` only.
  - `WS /accounts/ws/notifications` — budget threshold + burn-rate warnings, `USER` only.
  - Advisor reply channel — user-scoped, scoped by `request_id` correlation ([../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) FR6.2).
- **Auth:** the WebSocket upgrade handler MUST validate the JWT before accepting the connection. Rejected upgrades return HTTP 403 with `errorKey: "auth.forbidden"` ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) RBAC).
- **Broadcast model:**
  - Admin channels broadcast to all sockets authenticated with the corresponding role.
  - User channels (notifications, advisor reply) MUST scope fan-out by `account_id` — server-side filtering, never trust the client filter ([../../docs/business-rules/pfm-notifications-rules.md](../../docs/business-rules/pfm-notifications-rules.md) Cross-cutting).
- **Backpressure:** WebSocket sessions slow to drain are dropped; the client reconnects and backfills via the REST endpoint ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) FR3.2).

## 17. Configuration

- **Mechanism:** Quarkus MicroProfile Config. Values are read via `@ConfigProperty` or a `@ConfigMapping` interface (in adapter/wiring code only — `domain/` and `application/` stay framework-free). MUST NOT call `System.getenv` directly in production code.
- **Namespace convention:** application-owned properties live under `app.*` ([../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)). Quarkus framework properties keep their `quarkus.*` prefix.
- **Defaults:** every property documented in [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) MUST also exist in `application.properties` with the same default (or be marked `# required`).
- **Secrets** MUST NOT have committed defaults — `JWT_PRIVATE_KEY`, `LLM_API_KEY`, `DB_PASSWORD` MUST resolve to env vars only ([security.md §1](security.md#1-secrets-and-configuration)).
- **Profile boundaries:** `dev`, `test`, `prod`. A property documented for `prod` only ([../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)) MUST NOT be auto-populated in `test`.
