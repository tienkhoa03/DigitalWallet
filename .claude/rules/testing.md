# Testing — Digital Wallet

*How tests are written and run. The "what should be tested" comes from [docs/business-rules/](../../docs/business-rules/); this file is the implementation contract.*

> **Status:** no test code exists yet. Rules cite [docs/testing/README.md](../../docs/testing/README.md) and the spec. Code examples use module names from [docs/architecture/README.md §3–4](../../docs/architecture/README.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once a test runner lands.

## 1. Coverage targets

> See also: [docs/testing/README.md §4](../../docs/testing/README.md).

| Scope | Floor |
|---|---|
| Service layer (balance handling) | **80% line coverage** — mandated by [NFR4](../../docs/testing/README.md#4-coverage-targets) |
| Exception mappers, validators, idempotency middleware | **100% line + branch** |
| Repositories | every public method invoked at least once with a real DB |
| Frontend services | 80% line coverage |
| Frontend components with business logic | every reactive branch exercised |
| Generated code, DTO records, plain getters/setters | excluded from coverage |

Coverage is a **floor, not a target**. A service at 95 % with a missing test for the actual edge case is worse than 80 % with the right tests.

---

## 2. Backend Testing

### 2.1 Frameworks

- JUnit 5 (`org.junit.jupiter.api.*`).
- Mockito (`org.mockito.*`) for mocks of injected collaborators.
- AssertJ (`org.assertj.core.api.Assertions`) for fluent assertions and readable failure messages. `<!-- not-yet-adopted -->`
- Testcontainers — real Postgres + Kafka in integration profiles.

### 2.2 Mocking decision matrix

| Collaborator type | Approach |
|---|---|
| External I/O (HTTP client, Kafka producer) | Mock |
| Database via JPA | **Do not mock** — use Testcontainers Postgres |
| Redis | **Do not mock** in integration tests — use Testcontainers Redis |
| Filesystem | Mock unless the test is specifically about filesystem behaviour |
| `Clock` / `Instant.now()` | Inject a `Clock` and provide a fixed `Clock.fixed(...)` instance — never `PowerMock` static calls |
| Pure helper class | Do not mock — instantiate it |

### 2.3 Method naming

`<methodUnderTest>_<scenario>_<expectedOutcome>`:

```java
@Test
void transfer_withSameWallet_throwsSameWalletException() { ... }

@Test
void transfer_underConcurrentLoad_neverDoubleDebits() { ... }

@Test
void transfer_replayedWithSameKey_returnsCachedResponseWithoutDebiting() { ... }
```

### 2.4 Test database setup

- Each integration test class boots one Testcontainers Postgres, wired in via `@QuarkusTestResource(PostgresTestResource.class)` so Quarkus picks up the connection string at startup.
- Schema is applied via Flyway (`quarkus-flyway` runs on startup with `quarkus.flyway.migrate-at-start=true` in the test profile), identical to production migrations.
- Reset strategy: per-test transaction rollback (preferred) or per-class truncate.
- **Do not** use H2 — its dialect diverges from PostgreSQL on `numeric`, locks, and `ON CONFLICT`. The Quarkus dev-services H2 fallback is also disabled in the test profile.

### 2.5 Exception assertions

```java
assertThatThrownBy(() -> service.transfer(key, sameWalletReq))
    .isInstanceOf(SameWalletException.class)
    .hasMessageContaining("same wallet");
```

Never `try { … fail(); } catch (Exception e) { … }`.

### 2.6 Parameterized & boundary tests

```java
@ParameterizedTest
@ValueSource(strings = { "0", "-1", "0.00001" })
void transfer_withInvalidAmount_throwsValidation(String amount) { ... }
```

Boundary points to cover for every numeric limit:

- `0`, just above, just below.
- Maximum precision (`0.0001`) and one tick smaller (rejected).
- Page size: `0`, `1`, `100`, `101`.

### 2.7 Integration test profile

Quarkus splits unit and integration tests by file suffix: `*Test.java` runs in `mvn test`, `*IT.java` runs in `mvn verify` via the failsafe plugin. Integration tests use `@QuarkusIntegrationTest` (against the packaged artefact) or `@QuarkusTest` with Testcontainers wiring. `<!-- not-yet-adopted -->`

### 2.8 External API mocking

- Unit: mock the SmallRye `Emitter<T>` via Mockito; do not stand up a Kafka container for plain unit tests.
- Integration: Testcontainers Kafka wired via `@QuarkusTestResource` — exercises real topic semantics, not a stub.

### 2.9 Security test context

Every protected-endpoint test has both:

- An **unauthenticated** case (asserts `401`).
- An **authenticated-but-unauthorized** case (asserts `403`, where ownership applies).

See [security.md §11](security.md).

---

## 3. Frontend Testing

### 3.1 Location and naming

- Spec files co-located: `transfer-form.component.spec.ts` next to `transfer-form.component.ts`.
- One spec file per component or service. Don't bundle.

### 3.2 Arrange–Act–Assert

```ts
it('disables submit when amount exceeds balance', async () => {
  // Arrange
  const fixture = TestBed.createComponent(TransferFormComponent);
  fixture.componentRef.setInput('balance', 100);

  // Act
  fixture.componentInstance.amountControl.setValue(150);
  await fixture.whenStable();

  // Assert
  const submit = fixture.debugElement.query(By.css('[data-test="submit"]')).nativeElement;
  expect(submit.disabled).toBe(true);
});
```

### 3.3 Wrappers / providers

A shared `setupTestBed()` helper provides the standard provider list (router stub, HTTP testing module, fake `LocalStorageService`). `<!-- not-yet-adopted -->`

### 3.4 Query priority

In order of preference:

1. `data-test` attribute — `By.css('[data-test="submit"]')`.
2. ARIA role / accessible name — `getByRole('button', { name: /transfer/i })`.
3. Text content for static labels.
4. **Never** by class name or DOM structure — both are styling decisions, not contracts.

Add a `data-test` attribute to every interactive element a test depends on.

### 3.5 Mocking the network client

Use `HttpTestingController` from `@angular/common/http/testing`. **Do not** stub `HttpClient` with `jasmine.createSpy` — `HttpTestingController` enforces the request shape, headers, and method too.

### 3.6 Regression requirements

The XSS regression: every component that renders user-controlled text has a spec that feeds `<script>alert(1)</script>` and asserts the text appears as text, not as an executed script. See [security.md §11.1](security.md).

### 3.7 Async assertions

`whenStable()` for one-shot promises; `firstValueFrom(observable$)` for one-shot observables. Avoid `setTimeout` in tests — it creates flake.

---

## 4. Execution strategies

### 4.1 Fast feedback (developer loop)

| Goal | Command |
|---|---|
| Run a single backend test method | `./mvnw -Dtest=TransferServiceTest#transfer_withSameWallet_throwsSameWalletException test` |
| Run backend in dev mode (live reload, continuous testing) | `./mvnw quarkus:dev` |
| Run a single frontend spec | `npm test -- --include='**/transfer-form.component.spec.ts'` |
| Watch frontend specs | `npm test -- --watch` |

### 4.2 Full CI

| Goal | Command |
|---|---|
| Backend unit tests | `./mvnw test` |
| Backend full + integration | `./mvnw verify` |
| Frontend full | `npm test -- --watch=false --browsers=ChromeHeadless` |
| Coverage report | `./mvnw verify` (JaCoCo via Quarkus extension) + `npm test -- --code-coverage` |

`<!-- not-yet-adopted -->` Build tooling is not yet committed; commands above assume the Quarkus Maven wrapper + Angular CLI defaults — verify when scaffolding lands.

---

## 5. What not to test

- Generated code (Lombok output, OpenAPI clients) — implicitly covered when the consuming code is tested.
- Third-party library internals — assume their tests pass.
- Private methods — test through the public API. If a private method is so complex it needs direct testing, extract it to a helper class with its own public API and test that.
- Trivial getters/setters and constructor wiring.
- Framework-supplied behaviour (Angular `@Input` binding, JPA `@Id` generation).

A test that only asserts "the framework still does what it documents" is dead weight.

---

## 6. Test discipline

- **Never skip** a test (`@Disabled`, `xit`, `it.skip`) to make a build green. Either fix it, delete it, or quarantine it with a ticket reference in the annotation:
  ```java
  @Disabled("DW-123: flaky on Kafka rebalance — fix planned for sprint 14")
  ```
- Tests are **self-contained** — no shared mutable state between methods, no order dependence. A passing test must pass in isolation and in any order.
- One assertion theme per test. Multiple `assertThat` calls are fine when they all check the same outcome (e.g., shape + content of one response).
- A flaky test is a defect — investigate the root cause, do not retry-loop.
