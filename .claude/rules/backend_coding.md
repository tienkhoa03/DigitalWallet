# Backend coding rules

This file is the coding contract for every Quarkus / Java module under `backend/`. The `code-review` skill (step 4 onwards) checks pull requests against the sections below. Every rule is enforceable: a "MUST" failure is a release blocker; a "MUST NOT" describes a defect.

> **Status:** the codebase is not yet scaffolded. Every rule cites either `docs/` or a section of [../../project-info.md](../../project-info.md). Code examples use module names from [../../docs/architecture/README.md](../../docs/architecture/README.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

## 1. Project structure

The target module tree is defined in [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering); the organising principle (feature-based + layered) is stated there. Cross-feature import rules:

- A module's `api/` package MUST NOT import another feature's `service/`, `persistence/`, or `consumer/` packages. Cross-feature collaboration goes through Kafka topics ([../../docs/architecture/README.md §5](../../docs/architecture/README.md#5-how-modules-connect)) or an interface exposed in `shared/`.
- A module's `persistence/` package MUST NOT be imported from another feature's `api/` or `service/`. Reuse goes through a service in the owning module.
- The `pfm/` module MUST NOT have a JPA repository on `transaction`, `wallet`, or `outbox_event`. PFM state lives in Redis and the materialised view per NFR6 ([../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md), `Cross-cutting → Rule (no direct ledger UPDATE)`).
- Cross-cutting helpers (money type, idempotency middleware, outbox poller, security, lock helper, rate-limit middleware, audit-log writer) MUST live in `backend/shared/` and be imported by feature modules — never copied.
- `consumer/` and `event/` packages MUST NOT call JAX-RS resources; consumers invoke services in the same module directly.

## 2. Routing & controllers

- **Framework:** JAX-RS via RESTEasy Reactive, mandated by [../../project-info.md §4.1](../../project-info.md#41-backend).
- **Paths:** the canonical endpoint catalog lives in [../../docs/api/README.md](../../docs/api/README.md); MUST match it. New endpoints update the catalog in the same PR.
- **Path constants:** every resource MUST expose its base path as a `public static final String` and the JAX-RS `@Path` MUST reference it. Hard-coded path literals scattered across resources are a defect.
- **Response objects:** resources MUST return DTOs (see §6) or `Response`/`RestResponse` wrappers — Never expose JPA entities.
- **Required per-endpoint headers / middleware:**
  - Mutating money endpoints (deposit, withdraw, transfer) MUST require an `Idempotency-Key` header (NFR3, [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.2/FR1.3).
  - `POST /transfers` MUST pass through the Redis token-bucket rate limiter (10/min/user, [../../project-info.md §8](../../project-info.md#8-security-baseline)).
  - `POST /advisor/*` MUST pass through the Redis token-bucket rate limiter (5/hour/user).
  - The HTTP handler MUST NOT publish to Kafka directly — publishing is the outbox poller's job (NFR2/NFR5, [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) FR2.3).
- **RBAC:** controller-level `@RolesAllowed` is a hint; the authoritative check MUST also be at the service layer (see §3 and [security.md §3](security.md#3-authorization)).
- **Async request-reply:** advisor endpoints MUST return `HTTP 202` with a `request_id` and deliver the final payload over WebSocket (NFR8, [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) FR6.1).

Example shape `<!-- not-yet-adopted -->`:

```java
@Path(WalletPaths.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {

    public static final class WalletPaths {
        public static final String BASE = "/wallets";
        public static final String DEPOSIT = "/{walletId}/deposits";
    }

    private final WalletService service;

    public WalletResource(WalletService service) { // constructor injection only (§13)
        this.service = service;
    }

    @POST
    @Path(WalletPaths.DEPOSIT)
    @RolesAllowed("USER")
    public RestResponse<DepositResponse> deposit(
            @PathParam("walletId") UUID walletId,
            @HeaderParam("Idempotency-Key") @NotNull UUID idempotencyKey,
            @Valid DepositRequest request) {
        return RestResponse.ok(service.deposit(walletId, idempotencyKey, request));
    }
}
```

## 3. Service layer

- **Interface vs. impl:** a service MAY ship as a single class. An interface is only required when there is more than one production implementation, or when the service is consumed across feature modules. Do not invent `FooService` + `FooServiceImpl` pairs purely for habit.
- **Naming:** `<Noun>Service` for the business class, `<Verb><Noun>Service` only when behaviour is verb-shaped (e.g. `OutboxPublisherService`). Methods MUST be verb phrases (`deposit`, `transfer`, `recordFraudAlert`).
- **Transaction boundary:** `@Transactional` MUST be applied at the service method, NOT at the resource or repository. A method that both reads and writes the ledger MUST be `@Transactional` (default `REQUIRED`). Read-only flows SHOULD declare `@Transactional(TxType.SUPPORTS)` or omit the annotation entirely.
- **Hybrid concurrency (NFR1):** every wallet mutation MUST follow the exact order — (1) acquire the Redis distributed lock keyed on `wallet_id` with a short TTL via the helper in `shared/`; (2) open the `@Transactional` boundary; (3) read the wallet row with `LockModeType.PESSIMISTIC_WRITE`; (4) write the ledger row + outbox row; (5) commit; (6) release the Redis lock in a `finally`. See [../../docs/business-rules/README.md NFR1](../../docs/business-rules/README.md#nfr-enforcement-matrix) and [../../docs/decisions/0003-concurrency-strategy.md](../../docs/decisions/0003-concurrency-strategy.md).
- **Synchronous fraud pre-check (NFR9):** every wallet mutation MUST run the bounded fraud pre-check (velocity FR2.1 + volume FR2.2 Redis sliding-window lookups + `account.fraud_status` read FR2.4) **before** the Redis wallet lock is acquired. A breach rejects with the typed exception in §8 (`fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended` — error key preserved for API back-compat) and writes one `transaction.blocked` outbox event in a short `@Transactional` boundary — no ledger row, no wallet lock. *(MVP defers the `audit_log` row originally specified for blocks — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)* See [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) and [../../docs/decisions/0010-fraud-enforcement-model.md](../../docs/decisions/0010-fraud-enforcement-model.md).
- **Validation flow:**
  - Bean Validation on DTOs runs at the resource boundary (annotation-driven).
  - Cross-field and domain invariants (insufficient funds, FX missing, currency mismatch, recipient identity) run inside the service.
  - DB constraints are the last line (unique `(account_id, label)` on `wallet`, unique `(account_id, month)` on `budget`, idempotency-key uniqueness) — surfaced through the exception mapper (§8).
- **RBAC MUST run in the service layer**, not only at the controller. The service receives a `SecurityIdentity` or equivalent principal and rejects unauthorised callers with the typed exception in §8. Source: [../../project-info.md §8](../../project-info.md#8-security-baseline) and [security.md §3](security.md#3-authorization).
- **What MUST NOT happen on the request thread:**
  - Kafka publishing (§15, NFR2/NFR5).
  - LLM calls (NFR8).
  - Cross-event fraud analysis, alert fan-out (FR2.5), the suspension-policy decision (FR2.4), PFM aggregation, dashboard aggregation — these are Kafka-consumer concerns ([../../docs/business-rules/README.md NFR5](../../docs/business-rules/README.md#nfr-enforcement-matrix)). The bounded fraud pre-check (FR2.1 / FR2.2 Redis counters + `account.fraud_status` read) is the only fraud step permitted inline (NFR9; see the §3 hybrid-concurrency / pre-check rules).
  - Blocking I/O outside the configured worker / virtual-thread executor.

## 4. Data models / entities

- **PK strategy:** UUID (`uuid` Postgres column). Generated client-side, time-orderable variant preferred where ordering is useful (e.g. `uuidv7`). See [../../docs/database/README.md](../../docs/database/README.md#naming-conventions).
- **Date/time handling:** Java `Instant` or `OffsetDateTime` mapped to `timestamptz`. All timestamps are UTC ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)). MUST NOT use `Date`, `Calendar`, or `LocalDateTime` for stored timestamps.
- **Money columns:** `BigDecimal` in Java, `numeric(19,4)` in Postgres ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)). MUST NOT use `double` or `float` for money under any circumstance.
- **Event time:** `event_timestamp` on `transaction` is the event time carried into Kafka (NFR7, [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) Cross-cutting). Consumer code MUST use the event time from the Kafka payload, not `Instant.now()`.
- **Currency code:** ISO 4217, stored as `varchar(3)`. Java side MAY model it as a value object or a typed enum; the wire form is the three-letter string.
- **Relationships:** `@ManyToOne(fetch = LAZY)` is the default. `EAGER` fetch is forbidden on collection associations. Use explicit `JOIN FETCH` or a projection DTO when you need eager-like loading; this is the primary N+1 mitigation.
- **N+1 avoidance:** every multi-row read flow that crosses an association MUST be covered by an integration test that asserts the SQL count via Hibernate Statistics (or equivalent) `<!-- not-yet-adopted -->`. The list of indexed columns lives in [../../docs/database/README.md](../../docs/database/README.md#naming-conventions).

## 5. Data access

- **Repository style:** Hibernate ORM with Panache. Prefer the Panache Repository pattern (`PanacheRepositoryBase<T, UUID>`) over Active Record (`PanacheEntityBase` on the entity) — keeps entities free of static methods and makes mocking trivial in unit tests. Source: [../../project-info.md §4.1](../../project-info.md#41-backend).
- **Locking policy:** wallet reads inside the money path MUST use `LockModeType.PESSIMISTIC_WRITE`. The Redis distributed lock MUST be acquired first (NFR1). Acquisition order is fixed: **Redis lock → DB row lock → outbox write → commit → Redis lock release**. See [../../docs/business-rules/README.md NFR1](../../docs/business-rules/README.md#nfr-enforcement-matrix) and [../../docs/decisions/0003-concurrency-strategy.md](../../docs/decisions/0003-concurrency-strategy.md).
- **Query building:** prefer Panache `find` / `list` with named parameters, or JPQL with named parameters. MUST NOT concatenate string fragments containing untrusted input into queries (see [security.md §4](security.md#4-input-validation--injection)).
- **Native SQL** is permitted for materialised-view refresh, the outbox poller, and tight ledger reads where JPQL is expressively insufficient. Native SQL containing user-supplied input MUST use bound parameters.
- **Return types:** repositories return `Optional<T>` for single-row lookups, `List<T>` for multi-row, `Stream<T>` only when the caller is responsible for closing it within the same `@Transactional` scope. Never return `null`.
- **Pagination:** every list query that can grow unbounded MUST accept `(int page, int pageSize)` and apply a server-side cap (see §10).
- **Read flows are not lock flows:** statement queries (FR1.4) MUST NOT acquire write locks. The `wallet/persistence/` statement reader runs without `PESSIMISTIC_WRITE` ([../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.4).

## 6. DTOs

- **Naming:**
  - Request DTOs: `<Action><Noun>Request` — `DepositRequest`, `CreateBudgetRequest`, `OpenWalletRequest`.
  - Response DTOs: `<Noun>Response` — `WalletResponse`, `TransferResponse`, `BudgetResponse`.
  - Event payloads: `<Noun>Event` — `TransactionEvent`, `FraudAlertEvent`.
- **Shape:** Java `record` is the default. Use a class only when the DTO needs custom validation logic that cannot be expressed via Bean Validation, or when a record forbids a feature you genuinely need (very rare).
- **Never expose entities.** A JAX-RS resource MUST NOT return a JPA entity directly. A service method that fans out an entity over the API surface is a defect. Source: [../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide).
- **No bidirectional shapes.** Request DTOs and Response DTOs are separate types even when fields overlap. Reuse via shared field-level records (`MoneyAmount(BigDecimal value, String currencyCode)`) is encouraged.

## 7. Object mapping

- **Library:** hand-written mappers in `service/` or a dedicated `mapping/` package. MapStruct is allowed when it earns its keep on a DTO with ≥ 5 fields. Library choice is per module; once a module picks a library, MUST be consistent.
- **Null-value strategy for partial updates:** `PATCH` endpoints MUST distinguish "field omitted" from "field set to null". Use `Optional<T>` fields on the request DTO (or a wrapper sentinel) — never interpret a missing JSON property as "set to null" silently. Source: aligned with [../../docs/api/README.md](../../docs/api/README.md) which lists `PATCH /budgets/{budgetId}/buckets/{bucketId}/threshold`.

## 8. Exception handling

- **Custom base:** every domain exception MUST extend `shared.DomainException`, which carries an `errorKey` (dot-separated identifier) and a human message ([../../project-info.md §13](../../project-info.md#13-coding-conventions-highest-level-project-wide)).
- **Global mapper:** a single JAX-RS `ExceptionMapper<DomainException>` in `shared/` MUST own the HTTP envelope. Per-resource `try`/`catch` blocks that build JSON manually are a defect.
- **Error envelope:** the canonical shape is defined in [../../docs/api/README.md](../../docs/api/README.md#error-response-shape).
- **Status code mapping table** (authoritative; resources and services raise the typed exception, the mapper sets the status):

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
- **Method-level authorization:** in addition to controller-side `@RolesAllowed`, services MUST re-check the caller's role and ownership before mutating state ([security.md §3](security.md#3-authorization)).
- **CORS posture:** the application MUST start with a CORS allow-list (no `*` origin in any non-dev profile). Source: [security.md §5](security.md#5-transport--cors).
- **WebSocket handshake:** WebSocket upgrades MUST validate the JWT before accepting the connection ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) RBAC, [../../docs/business-rules/pfm-notifications-rules.md](../../docs/business-rules/pfm-notifications-rules.md) Cross-cutting).

## 10. Pagination & sort safety

This section is CRITICAL. Unsafe sort parameters are the most common JPQL-injection path.

- **Pagination parameters:** `page` (zero-based) and `pageSize`. Defaults: `page=0`, `pageSize=20`.
- **Caps:** `pageSize` MUST be capped server-side at `100`. Requests above the cap MUST be silently clamped (do not error — the contract is "best-effort up to 100").
- **Sort whitelist:** every endpoint that accepts a `sort` parameter MUST validate it against an explicit whitelist defined in the resource or service. Unknown sort keys MUST raise `validation.invalid_payload`.
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
- **Entity + migration in the same PR.** A JPA entity introduced or modified without a corresponding Flyway migration is a defect. The migration MUST be applied before the application code that depends on it merges.
- **No auto-DDL.** `quarkus.hibernate-orm.database.generation` MUST be `none` in every profile ([../../docs/database/migrations.md](../../docs/database/migrations.md#tool)).
- **Reference seeds** (currencies, FX rates, default categories) live in Flyway migrations; demo / fixture data lives under `backend/postgres/init/` (invoked by the Postgres container's `docker-entrypoint-initdb.d` hook in `backend/docker-compose.yml`) ([../../docs/database/migrations.md](../../docs/database/migrations.md#seed-data)).
- **Drop / rename** of columns or tables follows the staged pattern referenced in [../../docs/database/migrations.md](../../docs/database/migrations.md#conventions-for-the-migration-content): ship the read-side fallback first, then the rename in a subsequent release.

## 14. Unit tests

- See [testing.md §2](testing.md#2-backend-testing) for the full backend testing contract.
- **Framework:** JUnit 5 + Mockito ([../../project-info.md §4.5](../../project-info.md#45-testing--quality)).
- **Coverage floor:** ≥ 80% line coverage on the service layer (NFR4, [../../docs/business-rules/README.md](../../docs/business-rules/README.md#nfr-enforcement-matrix)). CI fails below the threshold.
- **Naming:** `methodUnderTest_givenCondition_thenExpectedOutcome` or behaviour-describing snake form (`transfer_with_replayed_idempotency_key_returns_original_outcome` — see [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline)).
- **Mocking matrix at a glance** (full matrix in [testing.md §2](testing.md#2-backend-testing)):

  | Collaborator | Unit tests | Integration tests |
  |---|---|---|
  | Postgres | Mock the repository | Testcontainers Postgres 16 |
  | Kafka producer | Mock the emitter | Testcontainers Kafka |
  | Kafka consumer | Drive the method directly | Testcontainers Kafka |
  | Redis | Mock the helper | Testcontainers Redis 7 |
  | LLM client | Mock | Wiremock or recorded fixture |
  | Time / clock | Inject a `Clock`; never call `Instant.now()` directly in service code | Same |

## 15. Messaging (Kafka)

- **Client:** SmallRye Reactive Messaging (Kafka extension) ([../../project-info.md §4.1](../../project-info.md#41-backend)).
- **Channel naming:** application channel names mirror the Kafka topic name, hyphenated: `transaction-events`, `fraud-alerts`, `pfm-threshold-alerts`. Advisor topics are TBD per [../../project-info.md §16](../../project-info.md#16-open-questions-to-answer-before-bootstrapping) item 1; once decided they MUST follow the same convention.
- **Producer pattern:** the request thread MUST NOT call `@Channel("...").send(...)`. The only producer is the outbox poller in `shared/`, driven by `@Scheduled` ([../../docs/decisions/0005-outbox-publisher.md](../../docs/decisions/0005-outbox-publisher.md), [../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) FR2.3). All other modules append to the outbox table.
- **Consumer pattern:** `@Incoming` methods MUST be idempotent (de-duplicate on outbox-event `id` — at-least-once delivery is the contract per NFR2). They MUST run on a consumer-managed executor, not on JAX-RS threads ([../../docs/business-rules/README.md NFR5](../../docs/business-rules/README.md#nfr-enforcement-matrix)).
- **Event-time correctness:** PFM and any downstream aggregator MUST use `event_timestamp` from the payload, not consumer wall-clock (NFR7, [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) Cross-cutting).
- **Failure handling:** a consumer that throws on a record MUST mark the record as failed (negative ack) and route to the per-topic DLQ (`<topic>.DLQ`) after a bounded retry count. The DLQ MUST be drained by a separate operational tool — Never silently swallowed.
- **Outbox poller:** drains rows in `created_at ASC` order; on publish success, sets `published_at` (see [../../docs/database/README.md](../../docs/database/README.md) `outbox_event`).

## 16. WebSockets

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

- **Mechanism:** Quarkus MicroProfile Config. Values are read via `@ConfigProperty` or a `@ConfigMapping` interface. MUST NOT call `System.getenv` directly in production code.
- **Namespace convention:** application-owned properties live under `app.*` ([../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)). Quarkus framework properties keep their `quarkus.*` prefix.
- **Defaults:** every property documented in [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) MUST also exist in `application.properties` with the same default (or be marked `# required`).
- **Secrets** MUST NOT have committed defaults — `JWT_PRIVATE_KEY`, `LLM_API_KEY`, `DB_PASSWORD` MUST resolve to env vars only ([security.md §1](security.md#1-secrets-and-configuration)).
- **Profile boundaries:** `dev`, `test`, `prod`. A property documented for `prod` only ([../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles)) MUST NOT be auto-populated in `test`.
