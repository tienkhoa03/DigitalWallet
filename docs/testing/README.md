# Testing

This page documents how the DigitalWallet test suite is structured, what the coverage floors are, and the discipline rules every contributor must follow. Source: [../../project-info.md §4.5](../../project-info.md#45-testing--quality), [../../project-info.md §6 NFR4](../../project-info.md#6-non-functional-requirements--invariants), [../../project-info.md §12](../../project-info.md#12-development-workflow).

## Frameworks per layer

| Layer | Framework | Notes |
|---|---|---|
| Backend unit | JUnit 5 + Mockito | Service-layer logic, value objects, exception mapping. Mock external collaborators; never mock the DB. |
| Backend integration | Testcontainers (Postgres 16 + Kafka + Redis 7) | Every public repository method and every Kafka consumer. Real containers — no embedded H2 or in-memory Kafka. |
| Backend coverage | JaCoCo | Reported under `target/site/jacoco/` `(spec — not yet implemented)`. CI gate fails the build below the floor. |
| Frontend unit | Vitest + React Testing Library | Reducers, selectors, hooks with branching logic. |
| Frontend coverage | c8 (via Vitest) | — |
| Frontend E2E | Playwright | One smoke per epic — see below. |

## Coverage targets

| Scope | Floor | Where enforced |
|---|---|---|
| Backend service layer (line) | ≥ 80 % (NFR4) | JaCoCo gate in GitHub Actions; CI fails below threshold. |
| Backend repository / consumer (functional) | Every public method tested under Testcontainers. | Code review + integration suite. |
| Frontend reducers / selectors / hooks | Every branching path. | c8 report + code review. |
| E2E smoke | One per epic. | Playwright suite. Suggested coverage: signup → transfer → see fraud alert; create budget → spend → see threshold alert (per [../../project-info.md §4.5](../../project-info.md#45-testing--quality)). |

## What NOT to test

- **Generated code.** OpenAPI client stubs, generated TypeScript types, JPA-emitted SQL.
- **Framework wiring.** Quarkus DI graph correctness — that's covered by application startup.
- **Trivial DTO accessors.** No `getX()`/`setX()` unit tests.
- **Trivial mappers** with no branching — covered indirectly by the service tests that consume them.
- **Tailwind utility CSS.** No visual regression tests in MVP `(verify)`.
- **Migration files themselves.** Test the resulting schema state instead, via Testcontainers.
- **Third-party libraries.** Trust them or replace them; don't re-test their contract.

## Test discipline

- **No `@Disabled` (JUnit) or `xit` / `it.skip` (Vitest) without a linked issue.** A skipped test without a ticket is a regression in CI quality and may be removed without notice.
- **No test order dependence.** Tests must pass when run in any order; do not share state through static fields, ambient containers, or external resources between tests.
- **One container set per test class** when using Testcontainers, started via `@QuarkusTestResource` or an equivalent lifecycle. Containers are reused across tests in the same class to keep CI under budget. `(verify pattern when wired)`
- **No PII in fixtures.** Use synthetic names and email addresses. The audit-log invariant (see [../business-rules/README.md](../business-rules/README.md)) applies to test logs as well.
- **Money is `BigDecimal`.** Tests must never assert on `double` or `float` for money — comparing scale matters (`BigDecimal("1.00").equals(BigDecimal("1.0"))` is `false`). Use `compareTo` for value equality.
- **Idempotency tests.** Every mutating endpoint has at least one test that posts the same body twice with the same `Idempotency-Key` and asserts identical responses (NFR3).
- **Concurrency tests.** Critical wallet operations (FR1.2, FR1.3) have at least one test that spawns concurrent requests against the same wallet to exercise the Redis lock + DB `FOR UPDATE` path (NFR1).
- **Event-time tests.** PFM aggregation tests inject out-of-order events to assert that `transaction_timestamp` (not wall-clock) drives bucket attribution (NFR7).
- **Coverage cannot be earned by junk assertions.** Reviewers reject tests that exist solely to bump JaCoCo numbers.
- **Test names describe behaviour.** Prefer `transfer_with_replayed_idempotency_key_returns_original_outcome` over `testTransfer1`.

## CI gate

Per [../../project-info.md §12](../../project-info.md#12-development-workflow), the blocking checks on every PR are:

1. Compile (backend + frontend).
2. Backend unit + integration tests (JUnit 5 + Testcontainers).
3. JaCoCo coverage gate — fails under 80 % on the service layer (NFR4).
4. Frontend lint.
5. Frontend tests (Vitest).

Playwright E2E may run as a non-blocking nightly suite in MVP `(verify when CI is wired)`.
