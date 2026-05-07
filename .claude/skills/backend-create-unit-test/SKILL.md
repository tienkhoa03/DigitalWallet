---
name: backend-create-unit-test
description: Use when the user asks to "write a unit test", "add tests for this service", "cover this method with tests", "test this controller/utility", or any equivalent. Generates JUnit 5 + Mockito tests for a Java class following the conventions in .claude/rules/testing.md and .claude/rules/backend_coding.md §14.
---

# Backend — Create Unit Test

Generate a `<Class>Test.java` for a target Java class, following the project's test conventions exactly. The user provides the class path; this skill produces a self-contained test file with the right scenario coverage.

## When NOT to invoke

- Integration tests that need a real Postgres or Kafka — those run with Testcontainers and live in a separate profile (see [testing.md §2.7](../../rules/testing.md)). State this clearly and stop; do not generate Testcontainers code from this skill.
- Frontend tests — different runner; use the frontend skill.
- Adding a single missing test method to an existing test class — just edit the file directly.

## Step 1 — Read the target class

Read the full source. Identify:

1. The public methods to be tested.
2. The injected collaborators (constructor params, `@Inject` fields).
3. Whether the class touches `Clock`/`Instant.now()` (must be injected and stubbed — see [testing.md §2.2](../../rules/testing.md)).
4. Whether the class throws domain exceptions (each one needs a test).

If the class has no public methods or only delegates trivially, stop and tell the user — there is nothing meaningful to test.

## Step 2 — Decide what to mock

Apply the matrix from [testing.md §2.2](../../rules/testing.md):

| Collaborator | Approach |
|---|---|
| HTTP client, SmallRye `Emitter<T>` (Kafka outgoing) | Mock |
| Panache repository / `EntityManager` in **unit** test | Mock the repository, not the EM directly |
| Redis client | Mock in unit; Testcontainers in integration |
| `Clock` | Provide `Clock.fixed(...)` — never mock `Instant.now()` statically |
| Pure helper class | Do NOT mock — instantiate it |

If a collaborator falls outside this matrix, ask the user via `AskUserQuestion`.

## Step 3 — Enumerate scenarios

For each public method, list scenarios in this order:

1. **Happy path** — typical inputs, expected outcome.
2. **Domain exception paths** — one per declared exception (e.g., `InsufficientFundsException`, `SameWalletException`).
3. **Null / empty input** — only if the method's contract accepts them; otherwise skip.
4. **Boundary values** — for every numeric or size parameter, test 0, just-above, just-below, and the documented limits (see [testing.md §2.6](../../rules/testing.md)).
5. **Concurrency / locking** — if the method takes a lock, add a test that asserts the lock is requested in the right order. Skip if not applicable.
6. **Idempotency** — if the method participates in idempotent flows, add a replay test asserting the second call returns the cached outcome without side effects.

## Step 4 — Generate the test file

Place at `backend/src/test/java/...` mirroring the production package. Class name: `<Class>Test`.

Required scaffolding:

```java
@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock TransferRepository repository;
    @Mock TransactionEventProducer producer;   // wraps the SmallRye Emitter
    @Mock IdempotencyStore idempotency;
    Clock clock = Clock.fixed(Instant.parse("2026-05-07T10:00:00Z"), ZoneOffset.UTC);

    TransferServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransferServiceImpl(repository, producer, idempotency, clock);
    }
    // tests...
}
```

Conventions to follow without exception:

- **Method name**: `<methodUnderTest>_<scenario>_<expectedOutcome>` ([testing.md §2.3](../../rules/testing.md)).
- **Assertions**: AssertJ — `assertThat(...)`. For exceptions, `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`. Never `try/fail/catch`.
- **Parameterized**: use `@ParameterizedTest` + `@ValueSource` (or `@CsvSource`) for boundary tests.
- **No `Thread.sleep`**, no `setTimeout`-equivalent, no order-dependent state. Each test is self-contained.
- **One assertion theme per test.** Multiple `assertThat` calls are fine if they all check one outcome (shape + content of one response).

## Step 5 — Self-check

- [ ] Every public method has at least one happy-path test.
- [ ] Every domain exception type the method throws has a dedicated test.
- [ ] Every numeric or size parameter has boundary coverage.
- [ ] `Clock` is injected and stubbed; `Instant.now()` is never called statically.
- [ ] No mock of `EntityManager`, no mock of pure helper classes.
- [ ] No `@Disabled` without a ticket reference in the annotation message.
- [ ] No shared mutable state between test methods.
- [ ] AssertJ is the only assertion library used (no JUnit `assertEquals`, no Hamcrest).

## Step 6 — Final report

Print:

- The test file written.
- The scenarios covered (numbered list).
- Any scenario that **could not** be covered as a unit test and should move to integration (e.g., "row-level lock acquisition requires real Postgres → integration test").
- Suggest invoking `backend-verify` next.
