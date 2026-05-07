---
name: code-review
description: Use when the user asks to "review this code", "code review", "review my changes", "check my PR", "does this look good", "review the diff", "check my changes", "review [filename]", or any equivalent. ALWAYS invoke this skill for any code-review request — never review ad-hoc. Loads the applicable rules from .claude/rules/ for each changed module and produces a structured review with a PASS/FAIL verdict.
---

# Code Review

Inspect the actual diff, load the rules that apply to the changed files, and produce a structured review. The output is reproducible: the same diff under the same rules yields the same review.

## Step 1 — Establish what to review

Run, in order:

```bash
git status --short
git rev-parse --abbrev-ref HEAD
git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main
git diff <merge-base>...HEAD --name-only
git diff <merge-base>...HEAD
```

If the user named a specific file ("review TransferResource.java"), narrow `git diff` to that path.

If there are uncommitted changes, also include `git diff` (working tree) — flag them in the report under "uncommitted".

If the working tree is clean and the branch has no commits beyond the merge-base, stop and tell the user there is nothing to review.

## Step 2 — Detect modules and load rules

Map each changed file to its rule set:

| File pattern | Rules to load |
|---|---|
| `backend/**/*.java` (production) | [.claude/rules/backend_coding.md](../../rules/backend_coding.md), [.claude/rules/security.md](../../rules/security.md), [.claude/rules/upgrade-policy.md](../../rules/upgrade-policy.md) |
| `backend/**/*Test.java` | [.claude/rules/testing.md](../../rules/testing.md), [.claude/rules/backend_coding.md §14](../../rules/backend_coding.md) |
| `backend/**/db/migration/*.sql` | [.claude/rules/backend_coding.md §13](../../rules/backend_coding.md), [docs/database/migrations.md](../../../docs/database/migrations.md) |
| `frontend/**/*.ts`, `frontend/**/*.html` (production) | [.claude/rules/frontend_coding.md](../../rules/frontend_coding.md), [.claude/rules/security.md](../../rules/security.md), [.claude/rules/upgrade-policy.md](../../rules/upgrade-policy.md) |
| `frontend/**/*.spec.ts` | [.claude/rules/testing.md §3](../../rules/testing.md), [.claude/rules/frontend_coding.md §11](../../rules/frontend_coding.md) |
| `docs/**/*.md` | content review only — check accuracy, not formatting |
| `.claude/rules/**`, `.claude/skills/**` | spell-check + cross-reference check |

Do NOT load rules for files that did not change. Loading more than needed dilutes the review.

## Step 3 — Walk every rule against the diff

For each loaded rule file, walk the numbered sections. For each section, scan only the **changed lines** (plus one line of context above/below) for violations. Note:

- A rule that doesn't apply to the changed files is skipped — do not include "no findings" entries.
- A rule that applies but is satisfied is not reported — only violations show up in the table.
- Severity is inferred from the rule's wording: "MUST" / "release blocker" → `block`; "MUST NOT" → `block`; "Never" / forbidden → `block`; "Prefer" / "Avoid" → `warn`.

Apply the [security.md §12 code-review checklist](../../rules/security.md) line-by-line as a final sweep. Each unchecked box that the diff should have triggered is a `block` finding.

## Step 4 — Report

Print this exact shape, in this order.

```
## Code Review

**Branch:** <branch>  →  **Base:** <merge-base ref>
**Files changed:** <n>  (`<list>`)
**Rules consulted:** <comma-separated rule files>

### Verdict: PASS | FAIL

| Rule | File:Line | Severity | Suggested fix |
|---|---|---|---|
| backend §10.2 sort whitelist | TransferResource.java:42 | block | Introduce `SORTABLE = Set.of("created_at", "amount")`; reject other keys with `INVALID_SORT_FIELD`. |
| security §3.2 ownership check | WalletResource.java:88 | block | Verify `wallet.accountId == currentAccountId` (or admin role) before reading the balance. |
| frontend §3.2 path constants | transfer.service.ts:18 | warn | Move literal `'/transfers'` into `API.TRANSFERS`. |

### Per-file detail

#### backend/src/.../api/TransferResource.java
- L42 — sort param flows untransformed into `repository.findAll(sort)`. The whitelist is mandatory in [backend §10.2](../../rules/backend_coding.md). Without it, `?sort=injected_clause` is a SQLi vector.
- L88 — ownership check absent; relevant rule [security §3.2](../../rules/security.md).

#### frontend/src/.../services/transfer.service.ts
- L18 — path string `'/transfers'` should be the constant `API.TRANSFERS` per [frontend §3.2](../../rules/frontend_coding.md).
```

### Verdict rules

- Any `block` finding → **FAIL**.
- Only `warn` findings → **PASS with warnings**.
- No findings → **PASS**.

### Edge cases

- **`<!-- not-yet-adopted -->` rules.** If the diff violates a rule that is itself flagged as not yet adopted (e.g., MapStruct mapping, MicroProfile OpenAPI, gitleaks), downgrade the severity to `info` and note "rule pending adoption — not blocking".
- **Auto-generated files** (lockfiles, migrations from a tool, OpenAPI clients): skip rule application; mention the file in the header but not in the table.
- **Documentation changes only**: switch to a content review — accuracy of paths, broken cross-references, conflict with code — and skip the rule walk.

## Step 5 — Discipline

- Do not invent rules during review. If a finding cannot point to a section of `.claude/rules/` or a `docs/` file, drop it.
- Cite the rule by file + section number — never paraphrase the rule.
- Do not suggest stylistic changes that the rules do not mandate. The review is rules-only.
