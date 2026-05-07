# Upgrade Policy — Digital Wallet

*The single source of truth for framework and library version baselines. Other rule files reference this table; do not duplicate version numbers elsewhere.*

> **Status:** no source code exists yet. Baselines are derived from the spec ([../../README.md §2](../../README.md), [docs/architecture/README.md §2](../../docs/architecture/README.md)). Replace `(verify)` rows with concrete versions once `pom.xml` / `package.json` are committed.

## 1. Supported baselines

| Library / Framework | Current target | Status | Source |
|---|---|---|---|
| Java | 21 (LTS) | Supported | [docs/architecture/README.md §2](../../docs/architecture/README.md) |
| Quarkus platform BOM | 3.x LTS (verify) | Supported | [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) |
| JAX-RS (Jakarta REST) impl | RESTEasy Classic via `quarkus-resteasy` | Supported | [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) |
| CDI | 4.x (Quarkus ArC) | Supported | implied by Quarkus 3 |
| JPA | 3.1 | Supported | implied by Quarkus 3 |
| Hibernate ORM | 6.x (Quarkus-managed; verify) | Supported | bundled with `quarkus-hibernate-orm-panache` |
| Hibernate ORM with Panache | matches Hibernate ORM (verify) | Supported | [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) |
| Narayana JTA | Quarkus-managed (verify) | Supported | bundled with `quarkus-narayana-jta` |
| SmallRye Reactive Messaging (Kafka) | Quarkus-managed (verify) | Supported | `quarkus-smallrye-reactive-messaging-kafka` |
| Quarkus WebSockets Next | Quarkus-managed (verify) | Supported | `quarkus-websockets-next` |
| Hibernate Validator | 8.x (Quarkus-managed; verify) | Supported | `quarkus-hibernate-validator` |
| Flyway | Quarkus-managed (verify) | Supported | [docs/decisions/0002](../../docs/decisions/0002-postgresql-with-flyway.md), `quarkus-flyway` |
| PostgreSQL | 16 (verify) | Supported | [docs/database/README.md](../../docs/database/README.md) |
| Apache Kafka | 3.x (verify) | Supported | [docs/decisions/0003](../../docs/decisions/0003-kafka-decouples-fraud-engine.md) |
| Redis | 7.x (verify) | Supported | [docs/architecture/README.md §2](../../docs/architecture/README.md) |
| Redisson | (verify) | Optional | [docs/decisions/0004](../../docs/decisions/0004-pessimistic-locking-balance-updates.md) |
| InfluxDB | 2.x (verify) | Optional | [docs/architecture/README.md §2](../../docs/architecture/README.md) |
| Angular | 17+ | Supported | [docs/architecture/README.md §2](../../docs/architecture/README.md) |
| TypeScript | 5.2+ (verify, Angular 17 baseline) | Supported | implied by Angular 17 |
| RxJS | 7.x (verify) | Supported | implied by Angular 17 |
| Tailwind CSS | 3.x (verify) | Supported | [docs/architecture/README.md §2](../../docs/architecture/README.md) |
| JUnit | 5 (Jupiter) via `quarkus-junit5` | Supported | [docs/testing/README.md](../../docs/testing/README.md) |
| Mockito | 5.x (verify) | Supported | [docs/testing/README.md](../../docs/testing/README.md) |
| Build tool (backend) | Maven with Quarkus platform BOM | Supported | [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md) |

This table is the single source for any version reference in [backend_coding.md](backend_coding.md), [frontend_coding.md](frontend_coding.md), [security.md](security.md), or [testing.md](testing.md). Do not embed version numbers in those files.

---

## 2. Migration posture

There are no legacy patterns yet — the codebase is brand new. This section tracks backlogged upgrades and their "new-code rules" once the project ages.

| Backlog item | Reason backlogged | New-code rule |
|---|---|---|
| _none_ | _none_ | — |

When the first deferred upgrade lands here, every entry MUST have a corresponding "new-code rule" so contributors know what is forbidden until the migration completes. Example shape:

> *(illustrative)* Hibernate 5 → 6 stuck behind a runtime upgrade. **New-code rule:** new entities use `@JdbcTypeCode` / `@JavaType`, never `@org.hibernate.annotations.Type` with raw type names.

---

## 3. Backend upgrade guardrails for new code

### 3.1 Use Jakarta-namespaced APIs

- Use `jakarta.*` packages exclusively. **Never** import `javax.persistence`, `javax.ws.rs`, `javax.inject` — those are Java EE 8 / Jakarta EE 8 and are forbidden.
- Spring Boot imports (`org.springframework.*`) are forbidden by [docs/decisions/0001](../../docs/decisions/0001-quarkus-over-spring-boot.md).
- Plain-Jakarta-EE deployment artefacts (`web.xml`, `application.xml`, `beans.xml` outside CDI extension hooks) MUST NOT be added — Quarkus handles these via build-time augmentation.

### 3.2 Hibernate 6 idioms

- `Session.load(...)` is deprecated — use `getReference(...)` or `find(...)`.
- Custom types use `@JdbcTypeCode` / `@JavaType` — not the legacy `@org.hibernate.annotations.Type` with string type names.

### 3.3 Records over `@Data` classes

DTOs are Java records when immutable (Java 21 native). Lombok is not introduced unless an ADR justifies it. `<!-- not-yet-adopted -->`

### 3.4 Java-21 features to prefer

- Pattern matching for `switch` over chained `if-instanceof`.
- Sealed interfaces for closed exception hierarchies (e.g., the `DomainException` family in [backend_coding.md §8.1](backend_coding.md)).
- `Optional.orElseThrow()` over `.get()`.
- Virtual threads for blocking I/O — only after Quarkus marks the integration stable for our extensions (verify before enabling).

### 3.5 Quarkus idioms for new code

- Prefer **Quarkus extensions** over raw libraries (e.g., `quarkus-redis-client` over a hand-rolled Lettuce setup). New libraries that have a Quarkus extension MUST use the extension.
- Persistence: **Panache Repository** over raw `EntityManager` for the common case; over Active Record for separation of concerns. See [backend_coding.md §5.1](backend_coding.md).
- REST: **RESTEasy Classic** (`quarkus-resteasy`) is the supported flavour. RESTEasy Reactive (`quarkus-resteasy-reactive`) is forbidden in new code without an ADR.
- Messaging: **SmallRye Reactive Messaging** (`@Channel`, `@Incoming`, `@Outgoing`) over the raw Kafka client.
- WebSockets: **Quarkus WebSockets Next** (`quarkus-websockets-next`) over the legacy JSR-356 wrapper (`quarkus-websockets`).
- Configuration: **Microprofile Config** via `@ConfigProperty`; `application.properties` with `%dev.*` / `%test.*` / `%prod.*` profile prefixes. Avoid `System.getenv()` / `System.getProperty()` in application code.
- Testing: `@QuarkusTest` for tests that need the container; `@QuarkusTestResource` for Testcontainers wiring.

---

## 4. Frontend upgrade guardrails for new code

### 4.1 Standalone-only

`NgModule` is forbidden in new code. See [frontend_coding.md §1.1](frontend_coding.md).

### 4.2 Signal inputs

`@Input()` decorator is acceptable on legacy code; **new components MUST use `input()` / `input.required()`** (Angular 17+ signal-based input API).

### 4.3 Functional APIs

- `HttpInterceptorFn` over class `HttpInterceptor`.
- `CanActivateFn` / `CanMatchFn` over class `CanActivate`.

### 4.4 New-style providers

- `provideRouter` (standalone) over `RouterModule.forRoot`.
- `provideHttpClient` over `HttpClientModule`.

### 4.5 Control flow

`@for`, `@if`, `@switch` (Angular 17+) over `*ngFor`, `*ngIf`, `*ngSwitch` for new templates.

---

## 5. When to break this policy

Two conditions justify deviating from the baselines:

1. **A critical CVE** in a dependency that has no upgrade path within the supported baseline. Document the workaround in [docs/decisions/](../../docs/decisions/) and patch out within 24 hours.
2. **A new feature** whose minimum version is above our baseline. Open an ADR before introducing the dependency. The ADR must answer: what feature, what version, what compatibility cost, what migration triggers a re-evaluation.

Anything else — "I prefer the new API", "the new version is shinier" — is not a reason. Wait for a coordinated upgrade cycle.

---

## 6. Accepted risks

Risks accepted by the team (e.g., a known CVE we cannot patch immediately) are tracked in [docs/decisions/](../../docs/decisions/) using the ADR template. Required fields:

- The CVE / risk ID and a link to the upstream advisory.
- The compensating control, if any (e.g., "WAF rule blocks the affected path").
- The condition that triggers re-evaluation (e.g., "patched in v3.4 — upgrade when v3.4 is on Maven Central").
- Sign-off from a named reviewer.

`<!-- not-yet-adopted -->` No accepted risks yet — the first one logs an ADR and adds a row to a future "Accepted risks" table here.
