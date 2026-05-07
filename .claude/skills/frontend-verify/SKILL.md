---
name: frontend-verify
description: Use when the user asks to "build the frontend", "run frontend tests", "verify the frontend", "check if the frontend compiles", "lint the frontend", "does the UI build?", or any equivalent. Also invoke proactively after any frontend code change. Runs lint → build → test sequentially, stops on the first failure, and reports a structured PASS/FAIL verdict. Always passes flags that prevent the test runner from hanging in non-interactive environments.
---

# Frontend — Verify

Run the frontend pipeline and report results. Sequential, fail-fast.

## Step 1 — Detect-and-skip preflight

```bash
test -f package.json && echo HAS_PKG || echo NO_PKG
test -f angular.json && echo HAS_NG || echo NO_NG
```

If `package.json` is missing (current state of the repo as of writing) **or** the frontend module hasn't been scaffolded yet, stop and emit:

```
PASS (skipped) — frontend module not yet scaffolded.
Missing: package.json (and angular.json) under frontend/ or repo root.
Once the module lands, re-invoke this skill.
```

Detect the package manager from the lockfile:

| Lockfile | Manager | Test runner default |
|---|---|---|
| `package-lock.json` | `npm` | Karma + Jasmine (Angular CLI default) |
| `pnpm-lock.yaml` | `pnpm` | same |
| `yarn.lock` | `yarn` | same |

## Step 2 — Pipeline

Run in order. **Stop on the first failure.**

| # | Step | Command (npm) | Pass criterion |
|---|---|---|---|
| 1 | Lint | `npm run lint` | exit 0 — **skip with note if no `lint` script exists** (ESLint is `<!-- not-yet-adopted -->` per [.claude/rules/frontend_coding.md §7](../../rules/frontend_coding.md)) |
| 2 | Build | `npm run build` (or `npx ng build --configuration=production` if no `build` script) | exit 0 |
| 3 | Tests | `npm test -- --watch=false --browsers=ChromeHeadless` | exit 0, no failed specs |

Substitute `pnpm`/`yarn` for `npm` where the lockfile dictates.

### Critical: prevent hangs in non-interactive environments

Karma's default config opens an interactive watcher and a real browser, which **hangs forever** in CI or any non-TTY shell. Always pass:

- `--watch=false` (single run, then exit)
- `--browsers=ChromeHeadless` (no GUI required)

If using Jest:

- `--watch=false`
- `--ci` (skip watch, fail on snapshot mismatch)

If the user has a custom test script that wraps these flags, prefer it; otherwise pass the flags explicitly. **Never** run `npm test` without one of the above — the process will not terminate.

## Step 3 — Report

```
## Frontend Verify

| Step | Status | Detail |
|---|---|---|
| 1. Lint   | PASS / FAIL / SKIPPED | <error count or "no lint script"> |
| 2. Build  | PASS / FAIL           | exit code |
| 3. Tests  | PASS / FAIL           | specs run / failed / skipped |

### Verdict: PASS / FAIL
```

On FAIL, append:

- The failing step number and its raw error excerpt (≤ 30 lines).
- For test failures: failing spec names with their assertion failure.
- One-line "next action".

On PASS, suggest invoking `backend-verify` if backend changes are also staged, or `code-review` before opening a PR.

## Discipline

- Never disable a failing spec (`xit`, `it.skip`) to make this skill pass — see [.claude/rules/testing.md §6](../../rules/testing.md).
- Never delete a `--watch=false` flag from the test command. The test runner will hang.
- Never run `--force` on the build to suppress an error — fix the error.
- If the lint step is skipped because no script exists, note it explicitly in the report rather than silently passing.
