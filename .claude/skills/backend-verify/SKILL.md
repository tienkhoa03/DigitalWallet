---
name: backend-verify
description: Use when the user asks to "build the backend", "run backend tests", "verify the backend", "does the backend compile", "check coverage", or any equivalent. Also invoke proactively after any backend code change. Runs a sequential pipeline (compile → test → coverage), stops on the first failure, and reports a structured PASS/FAIL verdict.
---

# Backend — Verify

Run the backend build pipeline and report results. Sequential, fail-fast.

## Step 1 — Detect-and-skip preflight

Before running anything, check the repo state:

```bash
test -f pom.xml && echo HAS_POM || echo NO_POM
test -d backend && echo HAS_BACKEND || echo NO_BACKEND
```

If `pom.xml` is missing (current state of the repo as of writing) **or** the backend module hasn't been scaffolded yet, stop and emit:

```
PASS (skipped) — backend module not yet scaffolded.
Missing: pom.xml (or build.gradle) at the repo root or under backend/.
Once the module lands, re-invoke this skill.
```

Do NOT attempt to run `mvn` against a missing project — it produces noisy errors that obscure the actual state.

## Step 2 — Pipeline

Run these in order. **Stop on the first failure.** Each step's command, log file, and pass criterion:

| # | Step | Command | Pass criterion |
|---|---|---|---|
| 1 | Compile | `./mvnw -q -DskipTests compile` | exit 0 |
| 2 | Unit tests | `./mvnw -q test` | exit 0, no failed tests |
| 3 | Verify (integration + coverage) | `./mvnw -q verify` | exit 0; JaCoCo service-layer line coverage ≥ 80 % |

The Quarkus Maven wrapper is the supported build entrypoint (`./mvnw`). If `mvnw` is missing, fall back to `mvn` from the path. Gradle is not part of the supported toolchain — see [docs/decisions/0001](../../../docs/decisions/0001-quarkus-over-spring-boot.md).

## Step 3 — Coverage reading

After step 3 completes, read the JaCoCo CSV at:

```
backend/target/site/jacoco/jacoco.csv
```

(JaCoCo is wired in via the Quarkus-recommended Maven plugin configuration.)

Compute per-package line coverage. The mandated floor — from [.claude/rules/testing.md §1](../../rules/testing.md) and [docs/testing/README.md §4](../../../docs/testing/README.md) — is **80 % on the service layer**. Any service-layer package below 80 % is a FAIL.

## Step 4 — Report

Print this exact shape:

```
## Backend Verify

| Step | Status | Detail |
|---|---|---|
| 1. Compile          | PASS / FAIL | exit code, error count |
| 2. Unit tests       | PASS / FAIL | tests run / failed / skipped |
| 3. Verify + coverage| PASS / FAIL | exit code |
| 4. Coverage floor   | PASS / FAIL | service-layer line %; floor 80 % |

### Verdict: PASS / FAIL
```

On FAIL, append:

- The failing step number and its raw error excerpt (≤ 30 lines).
- For test failures: the failing test method names and one-line failure reason each.
- For coverage failures: the offending package(s) and their actual percentage.
- A one-line "next action" — what the user should investigate.

On PASS, suggest the user invoke `frontend-verify` if a frontend change is staged, or `code-review` before opening a PR.

## Discipline

- Never skip a failing test to make this skill pass — see [.claude/rules/testing.md §6](../../rules/testing.md).
- Never lower the coverage floor without an ADR.
- Never run `--no-verify` or skip hooks to mask a failure.
