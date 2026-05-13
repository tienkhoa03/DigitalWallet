---
name: code-review
description: Walk the rule files against the current diff (or a named file) and emit a structured findings report with severities mapped from MUST / MUST NOT / Never (block), Prefer / Avoid (warn), `<!-- not-yet-adopted -->` rules (info). Apply the `security.md §12` checklist line-by-line. Invoke when the user asks to "review my changes", "code review this PR", "check my diff against the rules", "self-review the branch", "audit this file", "lint my changes against the standards", "do a security review of the diff", or "what would the reviewer flag here?".
---

# Code Review

This skill is the automated first-pass reviewer for changes against the `.claude/rules/` contract. It only cites — it does not invent rules and does not raise stylistic findings outside the rules.

## When NOT to invoke

- The user wants to verify the build / tests pass — use `backend-verify` or `frontend-verify`.
- The user wants help scaffolding new code — use `backend-create-rest-api` or `frontend-implement-ui-component`.
- The user wants a security-specific deep audit — there is a built-in `/security-review` skill from the harness; this skill performs the cross-cutting review.
- The user wants to open a PR — use `create-merge-request`.

## Step 1 — Establish what to review

Determine scope:

1. Run `git status --short` to see the working-tree state.
2. Determine the current branch via `git rev-parse --abbrev-ref HEAD`.
3. Compute the merge-base with the default branch: `git merge-base HEAD main`. Diff range is `<merge-base>..HEAD` plus the working tree.
4. Run `git diff --stat <merge-base>..HEAD` and `git diff <merge-base>..HEAD` to enumerate changed files and their content.
5. If the user named a file or directory in the prompt, narrow the scope to that path; otherwise review the full diff.

## Step 2 — Detect modules and load relevant rules

For each changed file path, map to a rule file:

| Path prefix | Rules to load |
|---|---|
| `backend/` `*.java` | [backend_coding.md](../../rules/backend_coding.md), [security.md](../../rules/security.md), [testing.md](../../rules/testing.md), [upgrade-policy.md §3](../../rules/upgrade-policy.md) |
| `backend/**/test/**` | [testing.md §2](../../rules/testing.md), [testing.md §6](../../rules/testing.md), [security.md §11](../../rules/security.md) |
| `backend/shared/db/migration/*.sql` | [backend_coding.md §13](../../rules/backend_coding.md), [docs/database/migrations.md](../../../docs/database/migrations.md) |
| `frontend/src/**` `*.ts(x)` | [frontend_coding.md](../../rules/frontend_coding.md), [security.md](../../rules/security.md), [testing.md §3](../../rules/testing.md), [upgrade-policy.md §4](../../rules/upgrade-policy.md) |
| `frontend/**/*.test.ts(x)` | [testing.md §3](../../rules/testing.md), [security.md §11](../../rules/security.md) |
| `docs/decisions/*.md` | [upgrade-policy.md §5](../../rules/upgrade-policy.md), [upgrade-policy.md §6](../../rules/upgrade-policy.md) |
| `docs/**` (other) | content review only — see Edge cases. |

Only load rule files that have changed-file matches. Skip rule files with no relevant diff.

## Step 3 — Walk rules against the diff

For every rule that applies to a touched file:

- Map the language of the rule to severity:
  - **MUST / MUST NOT / Never / "is a defect" / "is a release blocker"** → `block`.
  - **SHOULD / Prefer / Avoid** → `warn`.
  - **`<!-- not-yet-adopted -->`** sections → `info` (the rule describes a practice that holds once the relevant scaffolding lands; flag for awareness, do not block).
- For each finding, cite the rule section in the format `[backend_coding.md §N](../../rules/backend_coding.md)`. Use the exact section number from the rule file's heading.

Then apply the **[security.md §12](../../rules/security.md) checklist line-by-line at the end** — every checklist item is a release blocker; each unmet item is a `block` finding. Items in §12 are exhaustive and the canonical pre-merge list.

**Discipline:**

- Do not invent rules. If a concern is not in the rule files, leave it out — or surface it as a suggestion for `docs/decisions/` rather than a review finding.
- Always cite section numbers; reviewers should be able to click through.
- No stylistic findings outside the rules — formatting is the formatter's job per [project-info.md §12](../../../project-info.md) pre-commit hooks.
- An `info`-only file (only `<!-- not-yet-adopted -->` matches) does NOT block PASS.

## Step 4 — Structured report

Emit:

```
code-review report
──────────────────
Scope:
  Branch:       <current-branch>
  Merge base:   <sha>
  Files in scope (N):
    - <path>
    - <path>

Findings (X block / Y warn / Z info):

[block]  <path>:<line?>
  Finding: <one-line summary>
  Rule:    <citation>

[warn]   <path>:<line?>
  Finding: <one-line summary>
  Rule:    <citation>

[info]   <path>:<line?>
  Finding: <one-line summary> (not-yet-adopted; will apply once scaffolded)
  Rule:    <citation>

Security checklist (security.md §12):
  [ ] No secret material in the diff                     — PASS | FAIL
  [ ] No console.log / System.out.println in prod code   — PASS | FAIL
  [ ] Mutating money endpoints require Idempotency-Key   — PASS | FAIL | N/A
  [ ] RBAC at controller AND service layer               — PASS | FAIL | N/A
  ...(every item from security.md §12)

Verdict: PASS | FAIL
  - PASS if zero block findings AND every applicable §12 item passes.
  - FAIL otherwise.
```

## Edge cases

- **`<!-- not-yet-adopted -->` rules** — downgrade matched findings to `info`. The rule applies *once* the relevant code lands; flag for context, do not block.
- **Auto-generated files** — files marked auto-generated (top-of-file comment, OpenAPI clients, generated JPA SQL, generated TS types per [testing.md §5](../../rules/testing.md)) skip rule application. Note the skip in the report.
- **Docs-only changes** — if every changed path is under `docs/` and no rule file has changed, switch to a content review: check that ADR template fields from [upgrade-policy.md §5](../../rules/upgrade-policy.md) and [upgrade-policy.md §6](../../rules/upgrade-policy.md) are present where applicable; check cross-link integrity; do NOT raise coding-rule findings.
- **Rule files themselves changed** — surface the diff but do not apply other rule files against `.claude/rules/*.md`. The rules ARE the contract; their review is a human concern.
