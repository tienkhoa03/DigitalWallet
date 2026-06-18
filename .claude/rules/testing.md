# Testing rules

This file is the testing contract for DigitalWallet. The `code-review` skill checks pull requests against the sections below.

> **Status:** the codebase is not yet scaffolded. Every rule cites either [../../docs/testing/README.md](../../docs/testing/README.md), [../../project-info.md](../../project-info.md), or another rule file. Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

Source baselines: [../../docs/testing/README.md](../../docs/testing/README.md), [../../project-info.md §4.5](../../project-info.md#45-testing--quality), [../../project-info.md §6 NFR4](../../project-info.md#6-non-functional-requirements--invariants), [../../project-info.md §12](../../project-info.md#12-development-workflow).

## 1. Coverage targets

Coverage floors are derived from [../../project-info.md §4.5](../../project-info.md#45-testing--quality), [../../project-info.md §6 NFR4](../../project-info.md#6-non-functional-requirements--invariants), and [../../docs/testing/README.md](../../docs/testing/README.md#coverage-targets).

| Scope | Floor | Tool | Where enforced |
|---|---|---|---|
| Backend application-service layer (line) | ≥ 80 % (NFR4) | JaCoCo | GitHub Actions; CI fails below threshold ([../../project-info.md §12](../../project-info.md#12-development-workflow)). |
| Backend repository / consumer | Every public method covered under Testcontainers | JUnit 5 + Testcontainers | Code review + integration suite. |
| Frontend Pinia stores / getters / composables | Every branching path | Vitest + c8 | c8 report + code review. |
| Frontend components with logic | Smoke render + key interactions | Vitest + @testing-library/vue | Code review. |
| E2E smoke | One per epic | Playwright | Nightly suite (non-blocking in MVP — see [../../docs/testing/README.md](../../docs/testing/README.md#ci-gate)). |

A coverage drop on the application-service layer below 80% breaks the build — this is the NFR4 contract.

## 2. Backend testing

### 2.1 Frameworks

- **Unit:** JUnit 5 + Mockito ([../../project-info.md §4.5](../../project-info.md#45-testing--quality)).
- **Integration:** Testcontainers — Postgres 16, Kafka, Redis 7. Embedded H2 / in-memory Kafka are forbidden ([../../docs/testing/README.md](../../docs/testing/README.md#frameworks-per-layer)).
- **Coverage:** JaCoCo. Report under `target/site/jacoco/`. The ≥ 80 % gate (NFR4) targets the application-service layer (use-case impls) — `com/digitalwallet/*/application/service/**`. *(The `pom.xml` include pattern moves with the Java code restructure, a separate step; until then it still reads `*/service/**`.)*
- **HTTP integration:** RestAssured under `@QuarkusTest` `<!-- not-yet-adopted -->`.

### 2.2 Mocking decision matrix

| Collaborator | Unit tests | Integration tests |
|---|---|---|
| JPA repository / Panache | Mock with Mockito | Real Postgres via Testcontainers — Never mock the DB ([../../docs/testing/README.md](../../docs/testing/README.md#frameworks-per-layer)). |
| Kafka emitter / `@Channel` producer | Mock | Testcontainers Kafka; assert by consuming the topic in-test. |
| Kafka `@Incoming` consumer | Drive the method directly with a built payload | Testcontainers Kafka; publish a record and await effect. |
| Redis lock helper / rate limiter | Mock | Testcontainers Redis. |
| Outbox poller | Mock the publisher; assert outbox state | Testcontainers Postgres + Kafka. |
| LLM client | Mock | WireMock or recorded fixture; MUST NOT call the real LLM in CI. |
| `Clock` / `Instant.now()` | Inject a fixed `Clock`; application-service code receives a `Clock` rather than calling `Instant.now()` directly. | Same. |
| External HTTP | Mock | WireMock. |
| Time-windowed fraud rules (FR2.1, FR2.2) | Inject a `Clock`; advance manually | Use a fixed `Clock` and verify with a deterministic record sequence. |

### 2.3 Method naming

- Prefer behaviour-describing snake form: `transfer_with_replayed_idempotency_key_returns_original_outcome` ([../../docs/testing/README.md](../../docs/testing/README.md#test-discipline)).
- Alternative: `methodUnderTest_givenCondition_thenExpectedOutcome`.
- `testFoo1`, `testFoo2` are forbidden.

### 2.4 Test DB setup — Testcontainers vs in-memory policy

- **Policy:** Testcontainers for any test that touches Postgres, Kafka, or Redis. H2, embedded Kafka, embedded Redis are forbidden ([../../docs/testing/README.md](../../docs/testing/README.md#frameworks-per-layer)).
- **One container set per test class.** Containers are started via `@QuarkusTestResource` (or equivalent lifecycle) and reused across tests in the same class ([../../docs/testing/README.md](../../docs/testing/README.md#test-discipline)).
- **Database state:** every test MUST clean up after itself or run inside a transaction that is rolled back. Tests MUST NOT depend on ordering or on demo fixtures.
- **Schema source:** integration tests apply Flyway migrations on container startup. MUST NOT bypass Flyway with `hbm2ddl`.

### 2.5 Exception assertions

- Use `assertThatThrownBy(...)` (AssertJ) or `assertThrows(...)` (JUnit). MUST assert on the exception type AND on the `errorKey` for `DomainException` subclasses.
- A test that catches `Exception` and asserts only on the message is a defect — it passes when the wrong exception is thrown.

### 2.6 Parameterized & boundary tests

- **Parameterized:** use `@ParameterizedTest` with `@ValueSource` / `@MethodSource` for value-driven cases (currency codes, threshold percents, time windows).
- **Boundary tests** required for every numeric range:
  - `threshold_percent` at `0`, `1`, `100`, `101` ([../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) FR4.3).
  - Wallet `amount` at `0`, `0.0001`, negative, and overflow.
  - Fraud velocity at the threshold exactly (`5` per default 60 s window — boundary, not flag) and at threshold + 1 ([../../docs/business-rules/fraud-detection-engine-rules.md](../../docs/business-rules/fraud-detection-engine-rules.md) FR2.1).
  - Fraud volume at the threshold and at threshold + smallest currency unit.

### 2.7 Integration test profile

- The integration profile name is `test` (Quarkus default). All env vars from [../../docs/architecture/README.md §7](../../docs/architecture/README.md#7-config--profiles) MUST have safe `test` values or be overridden by the Testcontainers lifecycle.
- Integration tests MUST NOT read production secrets — fixtures inject synthetic JWT keys and a synthetic LLM key.

### 2.8 External API mocking

- The LLM provider (TBD per [../../docs/decisions/0002-llm-provider.md](../../docs/decisions/0002-llm-provider.md)) is mocked in CI via WireMock or a recorded fixture. CI MUST NOT make live LLM calls.
- The circuit-breaker open path MUST be covered by a test that forces consecutive simulated failures and asserts the response carries `errorKey: "advisor.circuit_open"` ([../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) Cross-cutting on circuit breaker).

### 2.9 Required NFR test contexts

Some tests are mandatory because the corresponding NFR is the system's contract:

| Context | Required tests | Source |
|---|---|---|
| Hybrid concurrency (NFR1) | Concurrent requests against the same wallet exercise Redis lock + DB `FOR UPDATE`; one wins, the loser sees `wallet.locked` or retries cleanly. | [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline), [../../docs/business-rules/README.md](../../docs/business-rules/README.md#nfr-enforcement-matrix) NFR1. |
| Idempotency (NFR3) | Same body + same key → identical response; different body + same key → `idempotency.replay_conflict`. | [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline) Idempotency tests. |
| Event time (NFR7) | An out-of-order event with an earlier `event_timestamp` lands in the correct month bucket. | [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline) Event-time tests. |
| Outbox (NFR2) | Ledger commit + outbox row are atomic; poller drains and marks `published_at`; consumer is idempotent on replay of the same outbox-event id. | [../../docs/business-rules/README.md](../../docs/business-rules/README.md#nfr-enforcement-matrix) NFR2. |
| LLM isolation (NFR8) | `POST /advisor/analyze` returns 202 within budget; the reply arrives on the WebSocket; circuit-breaker open returns `advisor.circuit_open`. | [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md). |
| No PFM writes on ledger tables (NFR6) | An integration assertion that `pfm/` writes never target `transaction`, `wallet`, or `outbox_event`. | [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) Cross-cutting. |

### 2.10 Security test context

See [security.md §11](security.md#11-testing-security-sensitive-code) for the required test matrix (unauthenticated / wrong-role / wrong-tenant / replay / boundary / XSS / rate-limit / outbox-event-on-block). *(MVP defers the audit-log test context — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)*

## 3. Frontend testing

See also [frontend_coding.md §11](frontend_coding.md#11-testing).

- **Co-location:** test files live next to the unit under test (`deposit-form.vue` + `deposit-form.test.ts`).
- **AAA layout:** Arrange / Act / Assert sections separated by blank lines. Tests with no clear AAA structure are a code-review reject.
- **Wrappers / providers:** tests render through a shared `renderWithProviders` helper that mounts Pinia, Vue Router, and a Vue Query client with a mock HTTP client. Tests MUST NOT manually wire each provider.
- **Query priority:** prefer in this order — `data-test` attribute (`getByTestId`) → ARIA role / name (`getByRole`) → visible text (`getByText`). Class names and CSS selectors are NEVER acceptable as test queries.
- **Network mocking:** mock at the Vue Query level (`msw` for HTTP, fake socket for WebSocket). MUST NOT stub global `fetch`.
- **XSS regression:** every component that renders user-supplied free text MUST have a test asserting that a string containing `<script>` is rendered as text (Vue text interpolation), not executed. See [security.md §11](security.md#11-testing-security-sensitive-code).
- **Async assertions:** use `findBy*` / `waitFor` from `@testing-library/vue`; MUST NOT rely on `setTimeout` with hard-coded waits. Flaky tests caused by missing `await findBy*` are defects (see §6).
- **Money formatting:** tests that assert on monetary values MUST compare on the decimal string emitted by the shared formatter, not on JS `Number` equality.
- **Coverage:** Vitest's c8 report covers Pinia stores, getters, and composables with branching logic ([../../docs/testing/README.md](../../docs/testing/README.md#coverage-targets)).

## 4. Execution strategies

### 4.1 Fast feedback

| Goal | Command |
|---|---|
| Run a single backend unit class | `./mvnw -pl backend test -Dtest=<ClassName>` `<!-- not-yet-adopted -->` |
| Run all backend unit tests | `./mvnw -pl backend test` |
| Run a single frontend test file | `pnpm --dir frontend test <path>` |
| Run all frontend unit tests | `pnpm --dir frontend test` |

### 4.2 Full CI

Per [../../project-info.md §12](../../project-info.md#12-development-workflow) and [../../docs/testing/README.md](../../docs/testing/README.md#ci-gate), every PR MUST pass:

1. Compile (backend + frontend).
2. Backend unit + integration tests (JUnit 5 + Testcontainers): `./mvnw -pl backend verify`.
3. JaCoCo coverage gate — fails under 80 % on the application-service layer (NFR4).
4. Frontend lint: `pnpm --dir frontend lint`.
5. Frontend tests: `pnpm --dir frontend test`.

Playwright E2E may run as a non-blocking nightly suite in MVP ([../../docs/testing/README.md](../../docs/testing/README.md#ci-gate)).

## 5. What not to test

From [../../docs/testing/README.md](../../docs/testing/README.md#what-not-to-test):

- **Generated code** — OpenAPI client stubs, generated TS types, JPA-emitted SQL.
- **Framework wiring** — Quarkus DI graph correctness is covered by application startup.
- **Trivial DTO accessors** — no `getX()` / `setX()` unit tests.
- **Trivial mappers** with no branching — covered indirectly by application-service tests.
- **Tailwind utility CSS** — no visual regression tests in MVP.
- **Migration files themselves** — test the resulting schema state instead.
- **Third-party libraries** — trust or replace; do not re-test their contract.
- **Private methods** — test through the public surface; if a private method is hard to cover, the public API is mis-shaped.
- **Framework behaviour** (Quarkus `@Transactional` semantics, Vue's reactivity batching) — assumed correct; tests assert your behaviour, not theirs.

## 6. Test discipline

Drawn from [../../docs/testing/README.md](../../docs/testing/README.md#test-discipline):

- **No `@Disabled` / `xit` / `it.skip` without a linked ticket.** A skipped test without a ticket is a regression in CI quality and MAY be removed.
- **Self-contained.** Tests pass in any order; do not share state through static fields, ambient containers, or external resources between tests.
- **One assertion theme.** A test asserts one behaviour. Multiple assertions inside a test are fine when they describe a single outcome (e.g. response status + response body shape); unrelated assertions belong in separate tests.
- **No PII in fixtures.** Synthetic names and email addresses. The no-PII-in-logs invariant applies to test logs as well ([../../docs/business-rules/README.md](../../docs/business-rules/README.md) Cross-cutting). *(MVP defers the `audit_log` table — see [../../docs/decisions/0009-rbac-roles.md](../../docs/decisions/0009-rbac-roles.md).)*
- **Money is `BigDecimal`.** Tests MUST NOT assert on `double` / `float` for money. Use `compareTo` for value equality — `BigDecimal("1.00").equals(BigDecimal("1.0"))` is `false`.
- **Coverage cannot be earned by junk assertions.** Reviewers reject tests that exist solely to bump JaCoCo numbers.
- **Test names describe behaviour.** Prefer `transfer_with_replayed_idempotency_key_returns_original_outcome` over `testTransfer1`.
- **Flaky tests are defects.** A flake MUST be either fixed (race condition, missing `await`, missing `Clock` injection) or quarantined under a ticket within one sprint. Re-running a flaky test until it passes is forbidden.
