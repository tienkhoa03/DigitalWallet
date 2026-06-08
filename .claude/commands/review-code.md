# Review Code

REVIEW ONLY — Do NOT fix, edit, or commit. This command is the user-facing entry point for a rule-based review of the current changes. It resolves the review scope, decides whether to run a single inline pass or fan out parallel specialist reviewers, drives the [`code-review`](../skills/code-review/SKILL.md) skill against the relevant rule files in [../rules/](../rules/), and returns one consolidated, advisory verdict.

Reviewing is not fixing. This command reports findings and (when asked) routes block-severity findings to the responsible developer agent — it never mutates code itself.

## Relationship to the `code-review` skill and the agent

- [`Skill("code-review")`](../skills/code-review/SKILL.md) is the **procedure** — it walks [../rules/](../rules/) against a diff and applies the [security.md §12](../rules/security.md#12-code-review-checklist--critical) checklist line-by-line. This command always runs that procedure.
- [`@code-reviewer`](../agents/code-reviewer.md) is the **specialist** — a read-only reviewer persona this command dispatches when the diff is large or spans both backend and frontend, so backend and frontend reviews run concurrently in isolated contexts.
- `/review-code` (this command) is the **orchestration** — scope resolution → mode selection → run → consolidate → report. Think `/make-plan` : `code-review` :: this command : the skill.
- This is distinct from the harness built-ins `/code-review`, `/review`, and `/security-review`. Those are generic; `/review-code` is bound to **this project's** rule files and is the canonical pre-PR self-review here.

## When NOT to invoke

- The user wants to confirm the build / tests pass — use [`Skill("backend-verify")`](../skills/backend-verify/SKILL.md) or [`Skill("frontend-verify")`](../skills/frontend-verify/SKILL.md). This command reviews the *source against the rules*, not the build result (though it can chain a verifier — see Step 4).
- The user wants new code scaffolded — use [`Skill("backend-create-rest-api")`](../skills/backend-create-rest-api/SKILL.md) or [`Skill("frontend-implement-ui-component")`](../skills/frontend-implement-ui-component/SKILL.md).
- The user wants to open a PR — use [`Skill("create-merge-request")`](../skills/create-merge-request/SKILL.md).
- The user wants the findings *applied*, not just reported — run this command first, then dispatch the responsible developer agent with the findings (see Step 5).

## Step 1: Resolve the review scope

Parse the argument and resolve exactly what to review. Run these in parallel:

- `git status --short` — working-tree state.
- `git rev-parse --abbrev-ref HEAD` — current branch.
- `git merge-base HEAD main` — merge-base with the default branch.

Map the argument to a scope:

| Argument form | Scope |
|---|---|
| _(empty)_ | The full branch diff `<merge-base>..HEAD` **plus** the uncommitted working tree. This is the default. |
| `staged` / "staged only" | `git diff --cached` only. |
| `working` / "uncommitted" | `git diff` plus untracked files. |
| A path (`backend/wallet/...`, a single file, a directory) | Narrow the diff to that path. |
| A git ref / range (`HEAD~3`, `abc123..def456`) | That range. |
| A PR number / URL (`#42`, a GitHub PR link) | `gh pr diff <n>` — review the PR's diff; note the PR in the report header. |

Enumerate the changed files with `git diff --stat <range>` and capture the content with `git diff <range>`. If the scope is empty (no changes at all), stop and tell the user there is nothing to review.

## Step 2: Detect surface area

Classify every changed path so the right reviewer and rule files load:

- **Backend** — anything under `backend/` (`*.java`, `*.sql` migrations, `application.properties`).
- **Frontend** — anything under `frontend/` (`*.ts`, `*.tsx`, config).
- **Docs / rules / config** — `docs/`, `.claude/rules/`, root `pom.xml` / `package.json` / `docker-compose.yml`.
- **Mixed** — both backend and frontend paths are present.

The rule-file mapping per path is owned by the [`code-review`](../skills/code-review/SKILL.md) skill (its Step 2). Do not duplicate that table here — this command only decides *who* runs the review.

## Step 3: Choose execution mode

| Condition | Mode |
|---|---|
| Small or single-surface diff (one module, ≲ ~15 changed files) | **Inline** — invoke [`Skill("code-review")`](../skills/code-review/SKILL.md) directly in this session. |
| Mixed surface (backend **and** frontend both changed) | **Parallel fan-out** — dispatch two [`@code-reviewer`](../agents/code-reviewer.md) agents in a single message (one scoped to `backend/`, one to `frontend/`), so the reviews run concurrently. |
| Large single-surface diff (many files, or the user asks for a "thorough"/"deep" review) | **Single agent** — dispatch one [`@code-reviewer`](../agents/code-reviewer.md) scoped to the changed paths, to keep the heavy rule-walking out of the main context. |

When dispatching `@code-reviewer`, set `subagent_type: code-reviewer` and give each agent: the exact scope (paths or range), the instruction "invoke `Skill(\"code-review\")` against your scope and return the structured report verbatim", and the reminder that it is **read-only — do not edit or fix**. Each agent's final message IS its findings report; relay it.

## Step 4: Run the review (and optionally verify)

- Run the chosen mode from Step 3.
- If — and only if — the user asked to "review and make sure it builds" (or similar), also chain the verifier(s) for the touched surface: [`Skill("backend-verify")`](../skills/backend-verify/SKILL.md) and/or [`Skill("frontend-verify")`](../skills/frontend-verify/SKILL.md). A clean review of source that does not compile is misleading; surface both results. Do not run verifiers by default — review and build are separate asks.

## Step 5: Consolidate and route

- Merge all reviewer outputs into one findings list, de-duplicated by `<path>:<line>` + rule citation.
- Preserve the skill's severity mapping: `block` (MUST / MUST NOT / Never / "is a defect" / "release blocker"), `warn` (SHOULD / Prefer / Avoid), `info` (`<!-- not-yet-adopted -->`).
- The verdict is **FAIL** if there is any `block` finding or any failed [security.md §12](../rules/security.md#12-code-review-checklist--critical) checklist item; otherwise **PASS** (warn / info do not fail).
- **Routing (advisory only):** if the user asked to fix the findings, dispatch each `block`/`warn` finding to the responsible developer agent — `@backend-developer` for `backend/` paths, `@frontend-developer` for `frontend/` paths — with the exact finding text, file, line, and rule citation. Otherwise, list the findings and stop. Never edit code from this command.

## Step 6: Report

Print a single consolidated report:

```
review-code report
───────────────────
Scope:        <range / paths / PR #>
Branch:       <current-branch>
Surface:      backend | frontend | mixed | docs
Mode:         inline | parallel fan-out | single agent
Files (N):    <list>

Findings (X block / Y warn / Z info):
  [block] <path>:<line>  — <summary>  (<rule citation>)
  [warn]  <path>:<line>  — <summary>  (<rule citation>)
  [info]  <path>:<line>  — <summary>  (not-yet-adopted)

Security checklist (security.md §12):  <PASS count> / <total>  (failures listed above as block)

Verifiers (only if requested):
  backend-verify:  PASS | FAIL | SKIPPED
  frontend-verify: PASS | FAIL | SKIPPED

Verdict: PASS | FAIL
Next steps:
  - <if FAIL> Address block findings, then re-run /review-code.
  - <if PASS> Ready to commit — consider /create-commit-plan, then Skill("create-merge-request").
```

## Important rules

- **DO NOT** edit, fix, or refactor code — this command is read-only. Routing block findings to a developer agent (Step 5) only happens when the user explicitly asks for fixes.
- **DO NOT** create git commits or open a PR — that is `/create-commit-plan` and `Skill("create-merge-request")`.
- **DO** always run the [`code-review`](../skills/code-review/SKILL.md) procedure — never hand-wave a review from memory; cite section numbers for every finding.
- **DO** apply the [security.md §12](../rules/security.md#12-code-review-checklist--critical) checklist line-by-line every time — each unmet item is a release blocker.
- **DO** fan out to [`@code-reviewer`](../agents/code-reviewer.md) for mixed or large diffs so backend and frontend review concurrently and the heavy rule-walking stays out of the main context.
- **DO NOT** invent rules. If a concern is not in [../rules/](../rules/), leave it out or suggest an ADR under [../../docs/decisions/](../../docs/decisions/) — do not raise it as a review finding.
