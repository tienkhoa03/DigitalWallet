---
name: backend-create-unit-test
description: Generate a JUnit 5 + Mockito unit test class for an existing backend Java class — enumerating scenarios for the happy path, each declared domain exception, boundary values, concurrency/lock paths, and idempotency replays. Invoke when the user asks to "write a unit test for X", "add tests for X service", "generate the test class for X", "cover this class with tests", "create JUnit tests for X", "add Mockito tests for X", "write coverage for X", or "give me a test scaffold for X".
---

# Backend — Create Unit Test

This skill generates the unit test for a single target class under `backend/` in the mirrored test package. It picks the mocking strategy from the rule matrix and only generates tests that assert real behaviour.

## When NOT to invoke

- The target needs Postgres / Kafka / Redis — those are integration tests with Testcontainers per [testing.md §2.1](../../rules/testing.md) and [testing.md §2.4](../../rules/testing.md), not unit tests covered here.
- The target is a DTO record or trivial mapper — those are excluded by [testing.md §5](../../rules/testing.md).
- The user wants to retrofit tests across many classes at once — break into per-class invocations.
- The user just wants to know test coverage — use `backend-verify` to read the JaCoCo report.

## Step 1 — Read the target class

Read the target file end-to-end. Identify:

- Public methods, their parameters, return types, and declared `throws`.
- Injected collaborators on the constructor (per [backend_coding.md §3](../../rules/backend_coding.md), constructor injection only).
- Whether the class injects a `Clock` (required for time-aware logic per [testing.md §2.2](../../rules/testing.md)) — if it calls `Instant.now()` directly inside time-dependent behaviour, surface this as a defect candidate.
- Domain exceptions thrown — every typed `DomainException` subclass needs an own scenario per [backend_coding.md §8](../../rules/backend_coding.md) and [testing.md §2.5](../../rules/testing.md).
- Whether the class is on the money path (Redis lock + `LockModeType.PESSIMISTIC_WRITE` sequence per [backend_coding.md §3](../../rules/backend_coding.md) and NFR1) or handles idempotency (NFR3).

## Step 2 — Decide what to mock

Apply the matrix from [testing.md §2.2](../../rules/testing.md):

| Collaborator | Decision |
|---|---|
| JPA repository / Panache | Mock with Mockito. |
| Kafka emitter / `@Channel` producer | Mock. |
| Kafka `@Incoming` consumer | Drive the method directly with a built payload. |
| Redis lock helper / rate limiter | Mock. |
| Outbox poller | Mock the publisher; assert outbox state via the mocked repo. |
| LLM client | Mock. |
| External HTTP | Mock. |
| `Clock` / `Instant.now()` | Inject a fixed `Clock`; the service code MUST receive a `Clock` per [testing.md §2.2](../../rules/testing.md). |
| Time-windowed fraud rules (FR2.1, FR2.2) | Inject a `Clock`; advance manually. |

If the matrix says "mock the DB" but the class needs real SQL semantics to be meaningful, stop and recommend an integration test under [testing.md §2.4](../../rules/testing.md) instead.

## Step 3 — Enumerate scenarios

Build the scenario list before writing any code:

1. **Happy path** — typical valid call returns expected result.
2. **Each declared `DomainException` subclass** — drive the precondition that triggers it; assert exception type AND `errorKey` per [testing.md §2.5](../../rules/testing.md).
3. **Boundary values** — apply [testing.md §2.6](../../rules/testing.md):
   - `threshold_percent` at `0`, `1`, `100`, `101`.
   - Wallet `amount` at `0`, `0.0001`, negative, overflow.
   - Fraud velocity at threshold and at threshold + 1.
   - Fraud volume at threshold and at threshold + smallest unit.
   - `@ParameterizedTest` with `@ValueSource` / `@MethodSource` for value-driven cases.
4. **Concurrency / lock path** — when applicable, assert Redis lock acquired before the DB call and released in `finally`; assert `LockModeType.PESSIMISTIC_WRITE` is requested before the mutation. Cover NFR1 per [testing.md §2.9](../../rules/testing.md).
5. **Idempotency replay** — for mutating money endpoints, assert same body + same key returns the original outcome; different body + same key returns `idempotency.replay_conflict`. Cover NFR3 per [testing.md §2.9](../../rules/testing.md).
6. **Event-time correctness** — for PFM-class consumers, assert use of `transaction_timestamp` from the payload rather than `Clock.instant()` per [testing.md §2.9](../../rules/testing.md).
7. **LLM circuit-open** — for advisor-class code, force consecutive failures and assert `advisor.circuit_open` per [testing.md §2.8](../../rules/testing.md) and [testing.md §2.9](../../rules/testing.md).
8. **Security context** — `unauthenticated`, `wrong role`, `wrong tenant` per [security.md §11](../../rules/security.md) and [testing.md §2.10](../../rules/testing.md) — only when the unit under test is the enforcement point (most are integration tests; flag and defer if so).

## Step 4 — Generate the test file

Write the test in the mirrored package at `backend/src/test/java/.../<Resource>Test.java` (or the project's standard test root once scaffolded). Conventions:

- **Class name:** `<ClassUnderTest>Test`.
- **Method name:** `<methodUnderTest>_<scenario>_<expectedOutcome>` or the behaviour-describing snake form (e.g. `transfer_with_replayed_idempotency_key_returns_original_outcome`) per [testing.md §2.3](../../rules/testing.md). `testFoo1` is forbidden.
- **Layout:** Arrange / Act / Assert separated by blank lines per [testing.md §3](../../rules/testing.md). One assertion theme per test per [testing.md §6](../../rules/testing.md).
- **Assertions:** `assertThatThrownBy(...)` (AssertJ) or `assertThrows(...)` (JUnit) — must assert exception type AND `errorKey` per [testing.md §2.5](../../rules/testing.md). `BigDecimal` equality via `compareTo`, never `.equals` per [testing.md §6](../../rules/testing.md).
- **No PII** in fixtures — synthetic names and emails per [testing.md §6](../../rules/testing.md).
- **No `@Disabled` / `it.skip`** without a linked ticket per [testing.md §6](../../rules/testing.md).
- **Clock:** inject a fixed `Clock` (`Clock.fixed(Instant, ZoneOffset.UTC)`) per [testing.md §2.2](../../rules/testing.md).

Skip what [testing.md §5](../../rules/testing.md) excludes (generated code, framework wiring, trivial accessors / mappers, private methods).

## Step 5 — Report

Report:

- The scenarios generated and the scenarios deliberately skipped (with the cited reason from [testing.md §5](../../rules/testing.md)).
- Any defect surfaced in the target class (e.g. direct `Instant.now()` use that blocks a `Clock`-based test — point at [testing.md §2.2](../../rules/testing.md)).
- Whether the class needs an integration test in addition (Testcontainers per [testing.md §2.4](../../rules/testing.md)).
- Suggested next step: run `backend-verify` to confirm the new tests compile and pass, and that the service-layer JaCoCo line floor in [testing.md §1](../../rules/testing.md) is still met.
