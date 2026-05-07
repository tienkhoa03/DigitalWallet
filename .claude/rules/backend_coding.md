# Backend Coding — Digital Wallet (Quarkus)

*How to write Java 21 backend code in this repo. Architecture and product behaviour live in [docs/](../../docs/); this file is the implementation contract.*

> **Status:** the backend module is not yet scaffolded. Every rule cites either [docs/](../../docs/) or a section of the project spec. Code snippets use module and class names from [docs/architecture/README.md §3](../../docs/architecture/README.md) and [docs/database/README.md](../../docs/database/README.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once the corresponding code lands; they will be re-grounded against real files at that point.

> **Stack note.** This project runs on Quarkus (see [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md)). All code uses `jakarta.*` packages — JAX-RS, CDI, JPA, Bean Validation, Transactions — provided by Quarkus extensions (`quarkus-resteasy`, `quarkus-hibernate-orm-panache`, `quarkus-narayana-jta`, `quarkus-hibernate-validator`, `quarkus-smallrye-reactive-messaging-kafka`, `quarkus-websockets-next`, etc.). Plain Jakarta-EE-runtime idioms (deployment descriptors, `application.xml`, server-managed JNDI lookups) do not apply.

## 1. Project structure

> See also: [docs/architecture/README.md §3](../../docs/architecture/README.md)

Backend code is organized **feature-first, then layered**. Every feature module (`wallet/`, `transaction/`, `fraud/`, `admin/`, `shared/`) contains the same internal layers:

| Layer | Folder | Responsibility |
|---|---|---|
| Presentation | `api/` | JAX-RS resources only — no business logic, no JPA |
| Business | `service/` | CDI beans, `@Transactional` boundaries, rule enforcement |
| Persistence | `persistence/` | JPA entities and Panache repositories |
| Event | `event/` | SmallRye Reactive Messaging emitters (when a feature emits) |
| Consumer | `consumer/` | SmallRye Reactive Messaging `@Incoming` listeners (when a feature consumes) |

### 1.1 Cross-feature rules

- Never import another feature's `service/` from your `api/`. Cross-feature collaboration goes through a service interface or Kafka.
- Never let `api/` access `persistence/` directly — go through `service/`.
- Place utilities used by ≥ 2 features under `shared/`. Single-use utilities stay local.
- A `wallet/`, `transaction/`, or `admin/` class MUST NOT import from `fraud/` — see [docs/business-rules/transfer-rules.md](../../docs/business-rules/transfer-rules.md#fraud-detection-runs-only-on-the-consumer-thread).

---

## 2. Routing & Controllers

> See also: [docs/api/README.md](../../docs/api/README.md) for the endpoint catalog.

Use JAX-RS (`jakarta.ws.rs.*`) only, served by **RESTEasy Classic** (the synchronous, blocking flavour shipped via `quarkus-resteasy`). No Spring `@RestController`. RESTEasy Reactive (`quarkus-resteasy-reactive`) is forbidden in new code unless an ADR commits to it — the transfer path is intentionally blocking and ACID-bound.

### 2.1 Resource class layout

```java
@Path(TransferResource.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class TransferResource {

    public static final String BASE = "/transfers";

    @Inject
    TransferService transferService;

    @POST
    public Response transfer(
            @HeaderParam("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid TransferRequest request) {
        TransferResult result = transferService.transfer(idempotencyKey, request);
        return Response.ok(TransferResponse.from(result)).build();
    }
}
```

### 2.2 URL constants

Define every JAX-RS path string as a `public static final` constant on the resource class. DTOs and other classes never construct URLs.

### 2.3 Path conventions

- Lowercase, kebab-case, plural collection nouns (`/transfers`, `/wallets`, `/fraud-alerts`).
- IDs are path params: `/wallets/{id}` — never query params for resource identity.
- Cross-resource queries are sub-paths: `/wallets/{id}/transactions`, not `/transactions?walletId=…`.

### 2.4 Response objects

Every `Response` body is a DTO defined in the same feature module. Returning a JPA entity from a resource is a release blocker (see §6).

### 2.5 Per-endpoint rules

| Endpoint | Required header | Body | Source rule |
|---|---|---|---|
| `POST /transfers` | `Idempotency-Key` | `TransferRequest` | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#idempotency-key-required) |
| `POST /wallets/{id}/withdraw` | — | `WithdrawRequest` | [wallet-rules.md](../../docs/business-rules/wallet-rules.md#withdrawal-does-not-overdraw) |
| `GET /wallets/{id}/transactions` | — | — | Paginated; see §10 |
| `GET /admin/metrics` | admin auth (verify) | — | FR3.1 |
| `WS /ws/admin/alerts` | admin auth (verify) | — | Subscribes to `fraud-alerts` |

### 2.6 Documentation annotations

Use SmallRye OpenAPI (`@Operation`, `@APIResponse`) on every public endpoint — provided by `quarkus-smallrye-openapi`. The generated spec is served at `/q/openapi` (Swagger UI at `/q/swagger-ui` in dev/test). Keep the table in §2.5 in sync with the annotations.

---

## 3. Service Layer / Business Logic

> See also: [docs/business-rules/](../../docs/business-rules/) for the rules services must enforce.

### 3.1 Interface vs. implementation

Public services expose a CDI-injectable interface; the implementation lives next to it.

```java
public interface TransferService {
    TransferResult transfer(String idempotencyKey, TransferRequest request);
}

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class TransferServiceImpl implements TransferService {
    // ...
}
```

Internal helpers MUST NOT have an interface. Keep them package-private.

### 3.2 Naming

| Artifact | Pattern | Example |
|---|---|---|
| Service interface | `<Domain>Service` | `TransferService` |
| Service implementation | `<Domain>ServiceImpl` | `TransferServiceImpl` |
| Helper class | `<Verb><Noun>` | `LockOrderResolver` |
| Domain exception | `<Reason>Exception` | `InsufficientFundsException` |

### 3.3 Transaction boundaries

- Services manage transactions. `api/` never opens one. `persistence/` never opens one.
- Annotate methods, not classes, when only some methods are transactional.
- `@Transactional(Transactional.TxType.REQUIRED)` is the default. Use `MANDATORY` on inner methods that must run in a caller's transaction. Quarkus uses Narayana JTA via `quarkus-narayana-jta`.
- Commit-then-publish ordering on the request thread is mandatory — see [docs/business-rules/transfer-rules.md](../../docs/business-rules/transfer-rules.md#commit-then-publish-ordering) and [docs/decisions/0006](../../docs/decisions/0006-outbox-or-publish-after-commit.md). With SmallRye Reactive Messaging, the Kafka emitter call MUST be issued **after** the transactional method returns (or use `@Outgoing` on a separate outbox-poller bean).

### 3.4 Validation flow

Validation runs at three layers, in order:

1. Bean Validation (`@Valid`, `@NotBlank`, `@DecimalMin`) on JAX-RS resource params — catches malformed input. Provided by `quarkus-hibernate-validator`.
2. Service-layer guards for business invariants (sender ≠ receiver, sufficient funds under lock).
3. DB constraint (`CHECK (balance >= 0)`) as the final backstop.

A guard at one layer does not remove the requirement at another layer.

### 3.5 No fraud logic on the request thread

Per [NFR5](../../docs/business-rules/transfer-rules.md#fraud-detection-runs-only-on-the-consumer-thread), `wallet/`, `transaction/`, and `admin/` services MUST NOT depend on `fraud/`. Enforce via ArchUnit once enabled. `<!-- not-yet-adopted -->`

---

## 4. Data Models / Entities

> See also: [docs/database/README.md](../../docs/database/README.md)

### 4.1 JPA mapping

```java
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    private long version;   // optimistic version, NOT a substitute for the lock
}
```

Entities are plain `@Entity` classes — Panache **Active Record** (`PanacheEntityBase` with static finders) is forbidden in this project so the persistence layer stays cleanly separated. Panache Repository (§5.1) is the supported pattern.

### 4.2 Primary key strategy

- Default: UUID (server-generated). Rationale: stable across services, unguessable, no insertion-order leak.
- ID type choice between UUID, ULID, and `bigserial` is open — see [docs/database/README.md §3](../../docs/database/README.md). Pick one project-wide; do not mix.

### 4.3 Date/time handling

- Always `OffsetDateTime` (or `Instant`). **Never** `java.util.Date`. **Never** `LocalDateTime` for persisted timestamps.
- DB columns are `timestamptz`.
- Inject a `Clock` into services — never call `Instant.now()` directly. This makes time testable without static mocking.

### 4.4 Money columns

- Java type: `BigDecimal`. **Never** `double` or `float`.
- DB type: `numeric(19,4)`.
- Negative values are rejected at every layer (see [wallet-rules.md](../../docs/business-rules/wallet-rules.md)).

### 4.5 Relationship configuration

- Default `FetchType.LAZY` on `@ManyToOne` and `@OneToOne`. The JPA default `EAGER` on `@ManyToOne` is forbidden — set it explicitly.
- Bidirectional only when both sides are queried. Otherwise unidirectional.
- Use `@JoinColumn(name = "...")` explicitly — never rely on naming defaults.

### 4.6 Avoiding N+1

- Use `JOIN FETCH` in JPQL or an entity graph for read paths that traverse a relationship.
- Add an integration test that asserts query count via `Hibernate.getStatistics()` for hot read paths. `<!-- not-yet-adopted -->`

---

## 5. Data Access / Repositories

### 5.1 Repository style

Use **Panache Repository** (`PanacheRepositoryBase<Entity, ID>`) from `quarkus-hibernate-orm-panache`. **Do not** add Spring Data JPA. **Do not** use Panache Active Record. Panache Repository keeps the persistence layer testable, leaves entities as POJOs, and gives us pessimistic-lock-friendly finder helpers.

```java
@ApplicationScoped
public class WalletRepository implements PanacheRepositoryBase<Wallet, UUID> {

    public Optional<Wallet> findByIdForUpdate(UUID id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
```

If a query needs the raw `EntityManager` (criteria, native SQL), inject it explicitly — Panache provides `getEntityManager()` on the repository, but **prefer** `@Inject EntityManager em;` for clarity.

### 5.2 Locking

Every read that precedes a balance write uses `LockModeType.PESSIMISTIC_WRITE`. See [transfer-rules.md](../../docs/business-rules/transfer-rules.md#pessimistic-lock-on-both-wallets) and [docs/decisions/0004](../../docs/decisions/0004-pessimistic-locking-balance-updates.md).

### 5.3 Lock acquisition order

Two-wallet locks are acquired in ascending `wallet.id` order to prevent deadlocks. See [transfer-rules.md](../../docs/business-rules/transfer-rules.md#lock-acquisition-order).

```java
UUID first  = id1.compareTo(id2) <= 0 ? id1 : id2;
UUID second = first.equals(id1) ? id2 : id1;
walletRepository.findByIdForUpdate(first);
walletRepository.findByIdForUpdate(second);
```

### 5.4 Query building

- Prefer Panache finders (`find("balance > ?1", min).list()`) or JPQL/Criteria over native SQL.
- Native SQL only for vendor-specific features (`numeric` cast, JSONB ops). Annotate the method with `@SuppressWarnings` and a comment naming the reason.
- All input goes through parameter binding (Panache positional `?1` or named `:name`). **Concatenating user input into JPQL or SQL is a release blocker.**

### 5.5 Return types

| Result shape | Return type |
|---|---|
| Single result, may be missing | `Optional<T>` (`findByIdOptional`, `find(...).firstResultOptional()`) — never `null` |
| Multiple results | `List<T>` |
| Streamed result | `Stream<T>` — only inside a transaction; close it |

### 5.6 Pagination

See §10 for the pagination contract. Panache provides `Page.of(page, size)` and `find(...).page(...).list()` — use those rather than manual `setFirstResult` / `setMaxResults`.

---

## 6. Data Transfer Objects (DTOs)

### 6.1 Naming

| Role | Suffix | Example |
|---|---|---|
| Inbound (request body) | `<Action><Noun>Request` | `TransferRequest`, `OpenWalletRequest` |
| Outbound (response body) | `<Noun>Response` | `TransferResponse`, `WalletResponse` |
| Internal projection | `<Noun>View` | `TransactionHistoryView` |

### 6.2 Shape

DTOs are Java records when immutable, plain classes when bean-style accessors are required (rare).

```java
public record TransferRequest(
        @NotNull UUID fromWallet,
        @NotNull UUID toWallet,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount) {
}
```

### 6.3 Never expose entities

A JAX-RS resource MUST NOT return a JPA entity, MUST NOT accept one. Every response goes through a DTO. This protects against:
- Lazy-loading exceptions outside the persistence context.
- Accidental field-level leaks (`password_hash`, internal flags).
- Coupling the wire format to the persistence model.

---

## 7. Object Mapping

### 7.1 Library

`<!-- not-yet-adopted -->` MapStruct is the candidate (it integrates with Quarkus via the standard annotation-processor config). Do not introduce ModelMapper or Dozer (reflection-based). Until a mapping library is committed, write explicit static factory methods on the DTO:

```java
public record TransferResponse(UUID transactionId, OffsetDateTime committedAt) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(result.transactionId(), result.committedAt());
    }
}
```

### 7.2 Null-value strategy for partial updates

- Full update (`PUT`): every field required; missing → 400.
- Partial update (`PATCH`): wrap optional fields in `JsonNullable<T>` to distinguish "not present" from "explicit null". `<!-- not-yet-adopted -->`

---

## 8. Exception Handling

### 8.1 Custom exception types

One base exception per feature; concrete exceptions extend it.

```java
public abstract class DomainException extends RuntimeException {
    private final String errorKey;
    protected DomainException(String errorKey, String message) {
        super(message);
        this.errorKey = errorKey;
    }
    public String errorKey() { return errorKey; }
}

public class InsufficientFundsException extends DomainException {
    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", "Insufficient funds on sender wallet");
    }
}
```

### 8.2 Global mapper

A single `@Provider ExceptionMapper<DomainException>` translates domain exceptions to HTTP responses.

```java
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    private static final Map<String, Integer> STATUS_BY_KEY = Map.of(
        "IDEMPOTENCY_KEY_REQUIRED", 400,
        "IDEMPOTENCY_KEY_CONFLICT", 409,
        "SAME_WALLET",              400,
        "WALLET_NOT_FOUND",         404,
        "INSUFFICIENT_FUNDS",       409
    );

    @Override
    public Response toResponse(DomainException ex) {
        int status = STATUS_BY_KEY.getOrDefault(ex.errorKey(), 400);
        return Response.status(status)
                .entity(new ErrorResponse(ex.errorKey(), ex.getMessage()))
                .build();
    }
}
```

### 8.3 Status code mapping

| Error key | Status | Source rule |
|---|---|---|
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#idempotency-key-required) |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#idempotency-key-conflict-on-a-different-payload) |
| `SAME_WALLET` | 400 | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#sender--receiver) |
| `INSUFFICIENT_FUNDS` | 409 | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#sender-must-have-sufficient-funds) |
| `WALLET_NOT_FOUND` | 404 | [docs/api/README.md](../../docs/api/README.md) |

### 8.4 Error response shape

```json
{ "error_key": "INSUFFICIENT_FUNDS", "message": "Insufficient funds on sender wallet" }
```

`message` is user-safe — never include stack traces, SQL fragments, or internal IDs (see [security.md §7](security.md)).

---

## 9. Security Configuration

> See also: [security.md](security.md) for the cross-cutting security rules.

### 9.1 Authentication

The auth scheme is unspecified by [the spec](../../docs/architecture/README.md#6-auth-flow). Until an ADR commits to one, every protected endpoint sits behind the Quarkus Security layer (`quarkus-smallrye-jwt` or `quarkus-oidc`, to be chosen) and rejects unauthenticated requests with 401. `<!-- not-yet-adopted -->`

### 9.2 CORS

Strict, explicit origin list configured via `quarkus.http.cors.*` properties. Never `*` in production. See [security.md §5](security.md).

### 9.3 Method-level authorization

Use `jakarta.annotation.security.RolesAllowed` once roles are defined. Default posture is **deny**: a resource without `@RolesAllowed` or `@PermitAll` MUST be unreachable through the Quarkus security pipeline.

```java
@POST
@Path("/admin/metrics")
@RolesAllowed("admin")
public Response metrics() { ... }
```

---

## 10. Pagination & Sort Safety

### 10.1 Pagination params

- `page` (zero-based) and `size`. Hard cap on `size`: 100. Default `size`: 20. `<!-- not-yet-adopted -->`
- Reject `size > 100` with `400 INVALID_PAGE_SIZE`.

### 10.2 Sort whitelist

Allow only an enumerated set of sort fields per endpoint:

```java
private static final Set<String> SORTABLE = Set.of("created_at", "amount");

if (!SORTABLE.contains(sort)) {
    throw new InvalidSortFieldException(sort);
}
```

Reject any sort key outside the whitelist. **Never** pass user input untransformed into `ORDER BY`.

### 10.3 Response envelope

```json
{
  "items":  [ ... ],
  "page":   0,
  "size":   20,
  "total":  152
}
```

---

## 11. Logging

### 11.1 Library

SLF4J via `org.slf4j.Logger`, backed by Quarkus' built-in JBoss Logging bridge (no extra dependency required).

```java
private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);
```

Log levels and structured-output keys are configured in `application.properties` (`quarkus.log.level`, `quarkus.log.console.json`, `quarkus.log.category."com.example".level`).

### 11.2 Placeholder syntax

Always parameterized — never string concatenation:

```java
log.info("transfer committed walletFrom={} walletTo={} amount={}",
        from, to, amount);
```

### 11.3 What to log per level

| Level | Use for |
|---|---|
| `ERROR` | Unhandled exceptions; events that page someone |
| `WARN`  | Business-rule rejections that may indicate attack (idempotency conflict, repeated insufficient funds) |
| `INFO`  | Successful state transitions (transfer committed, alert raised); one structured line per request |
| `DEBUG` | Branch decisions and computed values for active investigation; off in prod |
| `TRACE` | Method entry/exit; never enabled in prod |

### 11.4 Forbidden log content

- **Idempotency-Key values** in full (treat as request secrets — log last 4 chars only).
- Auth tokens, password hashes, full PII.
- Full request/response bodies — log identifiers and outcome only.

See [security.md §1.4](security.md).

---

## 12. Validation

### 12.1 Library

Jakarta Bean Validation 3.0 — `jakarta.validation.constraints.*` — provided by `quarkus-hibernate-validator`.

### 12.2 Creation vs. update

- Creation DTOs: every required field annotated.
- Update DTOs: required fields still annotated; truly optional fields use `Optional<T>` or `JsonNullable<T>`. `<!-- not-yet-adopted -->`

### 12.3 Custom validators

Place under `shared/validation/`. One annotation per file. Document the rule in [docs/business-rules/](../../docs/business-rules/) before implementing.

### 12.4 Resource-level validation

```java
@POST
public Response create(@Valid CreateWalletRequest req) { ... }
```

Bean Validation failures map to `400` via Quarkus' default `ResteasyViolationExceptionMapper` (overridable with a custom `@Provider`). `<!-- not-yet-adopted -->`

---

## 13. Database Migrations

> See also: [docs/database/migrations.md](../../docs/database/migrations.md)

Migrations are managed by `quarkus-flyway`. The extension auto-applies migrations on app startup when `quarkus.flyway.migrate-at-start=true`.

### 13.1 Forward-only

Never edit a committed migration. To undo, write a new compensating migration.

### 13.2 Naming

`V<n>__<short_snake_case>.sql` under `src/main/resources/db/migration/` — sequential, never reused. Seed data uses `V<n>__seed_<topic>.sql` or `R__seed_<topic>.sql`.

### 13.3 No auto-DDL

`quarkus.hibernate-orm.database.generation=validate` in every profile. **Never** `update`, `create`, `drop-and-create`, or `create-and-drop` outside a one-off test class. The non-`validate` modes are forbidden in committed `application.properties` (any profile).

### 13.4 Entity + migration in the same PR

A change that adds a column lands together: the migration, the entity update, the repository change, and a test that exercises the new column. Splitting these across PRs is forbidden.

---

## 14. Unit Tests

> See also: [testing.md](testing.md) for the full strategy.

- JUnit 5 + Mockito for plain unit tests.
- `@QuarkusTest` for tests that need the CDI container, Hibernate, or extensions wired up.
- Service-layer coverage floor: **≥ 80%** ([NFR4](../../docs/testing/README.md#4-coverage-targets)).
- Mock the SmallRye Reactive Messaging emitter (`Emitter<T>`) and external HTTP clients in pure unit tests; use Testcontainers + `@QuarkusTestResource` for persistence and Kafka integration tests.
- Test method names follow `<methodUnderTest>_<scenario>_<expectedOutcome>`:
  ```java
  void transfer_deductsSenderAndCreditsReceiver_underLock();
  void transfer_withSameWallet_throwsSameWalletException();
  ```
- Inject a `Clock` into services to make time deterministic in tests — never use `PowerMock` to stub `Instant.now()`.

---

## 15. Messaging (Kafka)

> See also: [docs/decisions/0003](../../docs/decisions/0003-kafka-decouples-fraud-engine.md) and [docs/decisions/0006](../../docs/decisions/0006-outbox-or-publish-after-commit.md).

Kafka access goes through **SmallRye Reactive Messaging** (`quarkus-smallrye-reactive-messaging-kafka`). Direct `KafkaProducer` / `KafkaConsumer` use is forbidden in new code.

### 15.1 Channel naming

Channels are configured in `application.properties`:

```properties
mp.messaging.outgoing.transaction-events.connector=smallrye-kafka
mp.messaging.outgoing.transaction-events.topic=transaction-events
mp.messaging.outgoing.transaction-events.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

mp.messaging.incoming.transaction-events-in.connector=smallrye-kafka
mp.messaging.incoming.transaction-events-in.topic=transaction-events
mp.messaging.incoming.transaction-events-in.group.id=fraud-engine
mp.messaging.incoming.transaction-events-in.value.deserializer=…
```

### 15.2 Producer (emitter) pattern

```java
@ApplicationScoped
public class TransactionEventProducer {

    @Inject
    @Channel("transaction-events")
    Emitter<TransactionCommittedEvent> emitter;

    public void publish(TransactionCommittedEvent event) {
        emitter.send(event);   // fire-and-forget at the messaging layer
    }
}
```

Emit **after** the JTA commit returns (commit-then-publish, [docs/business-rules/transfer-rules.md](../../docs/business-rules/transfer-rules.md#commit-then-publish-ordering)). For the Outbox path, a separate `@Outgoing` poller bean reads the outbox table on a Quarkus scheduled task.

### 15.3 Consumer pattern

```java
@ApplicationScoped
public class FraudVelocityConsumer {

    @Incoming("transaction-events-in")
    @Blocking                 // we use a blocking executor; rules touch JDBC/Redis
    public void onEvent(TransactionCommittedEvent event) {
        // rule evaluation; emit to fraud-alerts on violation
    }
}
```

Mark the consumer `@Blocking` because rule evaluation hits Postgres / Redis synchronously. Without it, SmallRye runs handlers on the event-loop thread and blocks it.

### 15.4 Failure handling

Configure dead-letter topics for poison messages: `mp.messaging.incoming.<channel>.failure-strategy=dead-letter-queue`. Never silently swallow a deserialisation error.

---

## 16. WebSockets

The admin fraud-alert push uses **Quarkus WebSockets Next** (`quarkus-websockets-next`), not the legacy `quarkus-websockets` (JSR-356 wrapper).

```java
@WebSocket(path = "/ws/admin/alerts")
public class FraudAlertSocket {

    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        return "subscribed";
    }

    public void onAlert(FraudAlert alert, OpenConnections connections) {
        connections.broadcastText(alert);
    }
}
```

The fraud `publisher/` feeds alerts into the socket via a CDI-injected service that the WebSocket endpoint subscribes to — typically a `BroadcastProcessor` exposed as an `@ApplicationScoped` bean.

---

## 17. Configuration

Configuration uses Microprofile Config via Quarkus' SmallRye implementation.

```java
@ApplicationScoped
public class FraudConfig {

    @ConfigProperty(name = "app.fraud.velocity.window-seconds", defaultValue = "60")
    int velocityWindowSeconds;

    @ConfigProperty(name = "app.fraud.volume.threshold", defaultValue = "50000")
    BigDecimal volumeThreshold;
}
```

- All app-specific keys are namespaced under `app.*`.
- Quarkus-built-in keys (`quarkus.*`, `mp.messaging.*`) live alongside in `application.properties`.
- Profiles use the `%dev.*`, `%test.*`, `%prod.*` prefix; reach for an env variable when the value is environment-specific.
