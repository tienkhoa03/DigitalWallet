---
name: frontend-verify
description: Run the frontend's local verification pipeline — lint, build, unit tests with Vitest — with the correct non-interactive flags for CI-style execution, and report a PASS/FAIL verdict. Invoke when the user asks to "run the frontend tests", "verify the frontend", "check the frontend builds", "run pnpm lint", "run Vitest", "is the frontend green?", "lint and test the UI", or "run the full frontend pipeline".
---

# Frontend — Verify

This skill runs the frontend's local verification pipeline: lint, build, tests. It uses non-interactive flags so the runner does not hang in a non-TTY shell.

## When NOT to invoke

- The frontend is not yet scaffolded — the skill detects this and exits early; do not re-invoke after the skip.
- The user wants to run a single test file — use the per-file command from [testing.md §4.1](../../rules/testing.md) directly.
- The user wants to run Playwright E2E — that is the nightly suite per [testing.md §1](../../rules/testing.md) and [testing.md §4.2](../../rules/testing.md); not part of this pipeline in MVP.
- The user wants backend verification — use `backend-verify`.

## Step 1 — Detect-and-skip preflight

Confirm `frontend/package.json` exists. If it does not, emit:

```
PASS (skipped) — frontend module not yet scaffolded.
```

…and stop. Do not run `npm`, `pnpm`, or any package-manager command against a missing project. The stack is mandated in [project-info.md §4.2](../../../project-info.md#42-frontend) and ADR #8.

Detect the package manager from the lockfile:

- `pnpm-lock.yaml` → `pnpm` (the mandated choice per [upgrade-policy.md §1](../../rules/upgrade-policy.md), [upgrade-policy.md §4](../../rules/upgrade-policy.md), and [frontend_coding.md §1](../../rules/frontend_coding.md)).
- `package-lock.json` or `yarn.lock` present → flag as a defect per [upgrade-policy.md §4](../../rules/upgrade-policy.md) (only `pnpm-lock.yaml` is allowed) before running the pipeline.

## Step 2 — Sequential pipeline

Run in order. Stop on the first failure and capture the tail of stderr/stdout for the report.

| Step | Command | Source |
|---|---|---|
| Lint | `pnpm --dir frontend lint` | [testing.md §4.2](../../rules/testing.md) |
| Build | `pnpm --dir frontend build` | [testing.md §4.1](../../rules/testing.md) (compile floor) |
| Tests | `pnpm --dir frontend test -- --run` (Vitest non-watch) | [testing.md §4.1](../../rules/testing.md), [testing.md §4.2](../../rules/testing.md) |

**CRITICAL — non-interactive flags:** the Vitest runner defaults to watch mode in a TTY. Always pass an explicit non-watch flag so the run terminates:

- Vitest (the mandated runner per [project-info.md §4.5](../../../project-info.md#45-testing--quality)): `--run` (preferred) or `--watch=false`.
- If the project still wires Jest anywhere: `--watch=false --ci`.
- If a Karma legacy script appears: `--watch=false --browsers=ChromeHeadless`.

A command that hangs is a defect of this skill — verify the flag is correct before reporting.

The Playwright E2E suite is **not** part of this pipeline; it is the nightly job per [testing.md §1](../../rules/testing.md). If the user explicitly asks for E2E, run `pnpm --dir frontend e2e` separately and surface the result outside the main verdict.

## Step 3 — Structured report

Emit:

```
frontend-verify report
──────────────────────
Lint                    : PASS | FAIL (N problems)
Build                   : PASS | FAIL
Tests                   : PASS | FAIL (N failing, N passed)

Failures:
  <log tail or summary>

Next step:
  <suggested action — e.g. fix the failing test, address the lint problems>
```

If any step fails, the overall verdict is `FAIL`. If the skill was skipped at Step 1, the overall verdict is `PASS (skipped)`.
