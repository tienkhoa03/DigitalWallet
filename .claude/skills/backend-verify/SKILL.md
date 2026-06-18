---
name: backend-verify
description: Run the backend build pipeline against the local project — compile, unit tests, integration tests with Testcontainers, and the JaCoCo service-layer coverage gate — and report a structured PASS/FAIL verdict per step. Invoke when the user asks to "run the backend tests", "verify the backend", "check coverage", "run mvn verify", "make sure the backend builds", "run the JaCoCo report", "is the backend green?", or "run the full backend pipeline".
---

# Backend — Verify

This skill runs the backend's full local verification pipeline: compile, unit tests, integration tests, coverage gate. It stops on the first failure so the next step's signal is not noise.

## When NOT to invoke

- The backend is not yet scaffolded — the skill itself detects this and exits early; do not re-invoke after the skip.
- The user wants to run a single test class — use the per-test command from [testing.md §4.1](../../rules/testing.md) directly rather than the full pipeline.
- The user wants to inspect coverage without running tests — read the existing `target/site/jacoco/jacoco.csv` directly.
- The user wants frontend verification — use `frontend-verify` instead.

## Step 1 — Detect-and-skip preflight

Before running any build tool, check that the backend is scaffolded. Read the repo root to confirm `backend/pom.xml` (and `backend/mvnw`) exist.

If they do **not** exist, emit:

```
PASS (skipped) — backend module not yet scaffolded.
```

…and stop. **CRITICAL:** do not run `mvn`, `./mvnw`, or any build tool against a missing project. The mandate is Maven per [upgrade-policy.md §1](../../rules/upgrade-policy.md) and ADR #7; if `pom.xml` is absent, there is nothing to invoke.

## Step 2 — Sequential pipeline

Run the pipeline in order. Stop on the first failure and capture the tail of stderr/stdout for the report.

| Step | Command | Source |
|---|---|---|
| Compile | `./mvnw -pl backend compile` (or `./mvnw clean install -DskipTests` if multi-module) | [testing.md §4.1](../../rules/testing.md) |
| Unit tests | `./mvnw -pl backend test` | [testing.md §4.1](../../rules/testing.md) |
| Integration tests + coverage | `./mvnw -pl backend verify` | [testing.md §4.2](../../rules/testing.md) |

Integration tests run via Testcontainers (Postgres 16 + Kafka + Redis 7) per [testing.md §2.1](../../rules/testing.md) and [testing.md §2.4](../../rules/testing.md). The Docker daemon must be reachable; if it is not, surface that as a `FAIL` with a clear diagnostic and stop — do NOT silently fall back to in-memory substitutes ([testing.md §2.4](../../rules/testing.md) forbids them).

## Step 3 — Coverage reading

After `verify` completes, parse `backend/target/site/jacoco/jacoco.csv`. For every row whose `PACKAGE` matches a feature module's application-service package (`*.application.service.*` in the target hexagonal layout per [backend_coding.md §1](../../rules/backend_coding.md); until the Java code restructure lands these packages are still `*.service.*`, which the `pom.xml` include pattern still targets), compute line coverage as `(LINE_COVERED) / (LINE_COVERED + LINE_MISSED)`.

The application-service-layer line floor is **≥ 80 %** per [testing.md §1](../../rules/testing.md) and NFR4. If any application-service-package row is below the floor, mark this step `FAIL` with the offending package name and the actual percentage.

**Discipline:**

- Never skip a failing test by adding `@Disabled` — [testing.md §6](../../rules/testing.md) forbids it.
- Never lower the 80 % floor in this skill or its outputs — the floor is the NFR4 contract; changing it requires the ADR procedure in [upgrade-policy.md §5](../../rules/upgrade-policy.md).
- A flaky test is a defect, not a re-run candidate — [testing.md §6](../../rules/testing.md).

## Step 4 — Structured report

Emit a report in this shape:

```
backend-verify report
─────────────────────
Compile                 : PASS | FAIL (details below)
Unit tests              : PASS | FAIL (N failing, N errors)
Integration tests       : PASS | FAIL (N failing, N errors)
JaCoCo coverage gate    : PASS | FAIL — service-layer floor is 80% per testing.md §1

Per-service coverage:
  - <module>.service     : <pct>%
  - <module>.service     : <pct>%

Failures:
  <log tail or summary>

Next step:
  <suggested action — e.g. fix the failing test, raise coverage by adding tests via backend-create-unit-test>
```

If any step fails, the overall verdict is `FAIL`. If the skill was skipped at Step 1, the overall verdict is `PASS (skipped)`.
