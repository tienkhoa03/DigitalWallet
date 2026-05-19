---
name: create-merge-request
description: Open a GitHub pull request from the current branch — pre-flight checks, handle uncommitted changes via confirmation, resolve working and target branches, push with upstream tracking, and draft a Conventional-Commits-aligned title and body (Summary, Changes, Test Plan, optional Risk) via `gh pr create`. Invoke when the user asks to "open a PR", "create a pull request", "raise the PR for this work", "push and open the merge request", "submit my branch for review", "make a draft PR", "open MR", or "ship the branch".
---

# Create Merge Request

This skill opens a GitHub pull request from the current branch, mirroring the workflow committed in [project-info.md §12](../../../project-info.md#12-development-workflow) (GitHub pull requests, Conventional Commits, branch model). The platform CLI is `gh`.

## When NOT to invoke

- The branch is not ready — run `code-review`, `backend-verify`, and `frontend-verify` first.
- The user wants to push without opening a PR — run `git push` directly; this skill always opens a PR.
- A PR already exists for the branch — surface the existing URL instead of opening a duplicate.
- The user wants to merge an existing PR — that is `gh pr merge`, not this skill.

## Step 1 — Pre-flight

Run in parallel:

- `git status --short` — capture uncommitted state.
- `git rev-parse --abbrev-ref HEAD` — current branch.
- `git remote get-url origin` — confirm a remote is configured.
- `gh auth status` — confirm `gh` is authenticated against the right host.

Stop with an actionable message if `gh auth status` fails or the remote is missing. The PR platform per [project-info.md §12](../../../project-info.md#12-development-workflow) is GitHub; do not substitute another tool.

## Step 2 — Handle uncommitted changes

If `git status --short` reports any modification, ASK via a single `AskUserQuestion` call. Never silently commit:

- **Commit all** — stage and commit every modified file with a user-provided Conventional-Commits message per [project-info.md §12](../../../project-info.md#12-development-workflow).
- **Commit specific files** — ask which files to stage, then commit.
- **Stash** — `git stash push -u -m "create-merge-request stash"`; warn the user the stash will be left in place.
- **Cancel** — stop the skill without taking action.

If the user picks a commit option, draft the commit message in Conventional Commits style (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:` — the exact prefixes from [project-info.md §12](../../../project-info.md#12-development-workflow)) using a HEREDOC. Run pre-commit hooks normally (gitleaks per [security.md §10](../../rules/security.md#10-secret-scanning), formatter, fast lint per [project-info.md §12](../../../project-info.md#12-development-workflow)); on hook failure, fix the underlying issue and create a NEW commit — do NOT pass `--no-verify`.

## Step 3 — Resolve working branch

If the current branch is the default (`main`), refuse to push directly. Per [project-info.md §12](../../../project-info.md#12-development-workflow) the model is trunk-based with short-lived feature branches off `main`; PRs land via branches only. Ask the user for a new branch name following the project's branch convention and run `git checkout -b <name>`.

If the current branch is not `main`, proceed.

## Step 4 — Identify target branch

Default target is the repository default branch (typically `main`). Run `gh repo view --json defaultBranchRef -q .defaultBranchRef.name` to confirm. If the user named a different target in the prompt, use that instead.

## Step 5 — Push

Check whether the branch tracks a remote:

- If no upstream is set, push with `-u`: `git push -u origin <branch>`.
- If the remote branch is ahead and the push fails fast-forward, **stop** and surface the conflict. Do NOT force-push without an explicit user instruction. If the user explicitly asks for a force push, prefer `git push --force-with-lease` over `--force`, and refuse outright if the target is the default branch.

## Step 6 — Draft title and body

**Title:** Conventional Commits prefix per [project-info.md §12](../../../project-info.md#12-development-workflow), imperative mood, ≤ 70 characters total. Examples: `feat: add wallet deposit endpoint`, `fix(transfer): reuse idempotency key on retry`, `refactor(pfm): move budget reader to materialized view`.

**Body** sections (use a HEREDOC for the `gh pr create --body` argument):

```
## Summary
1–3 bullets on the why. Tie back to the FR / NFR or business-rule file that motivated the change
(e.g. "implements FR1.3 transfer — see docs/business-rules/core-wallet-management-rules.md").

## Changes
- High-level list of what changed, grouped by feature module.

## Test Plan
- [ ] Backend unit + integration tests (Testcontainers) — `./mvnw -pl backend verify` per testing.md §4.2.
- [ ] JaCoCo service-layer floor ≥ 80% — per testing.md §1, NFR4.
- [ ] Frontend lint — `pnpm --dir frontend lint` per testing.md §4.2.
- [ ] Frontend tests — `pnpm --dir frontend test --run` per testing.md §4.2.
- [ ] Add any feature-specific manual verification steps here.

## Risk (optional)
Anything that touches NFR1 (hybrid concurrency), NFR2 (outbox), NFR3 (idempotency),
NFR5 (latency isolation), NFR6 (CQRS), NFR7 (event time), or NFR8 (LLM isolation)
gets an explicit risk note here.
```

## Step 7 — Open the PR

Invoke:

```
gh pr create \
  --title "<title>" \
  --base "<target-branch>" \
  --head "<working-branch>" \
  --body "$(cat <<'EOF'
<body from Step 6>
EOF
)"
```

If the user asked for a draft PR, add `--draft`.

## Step 8 — Print PR URL and next steps

Emit:

```
create-merge-request report
───────────────────────────
PR opened:    <URL>
Title:        <title>
Source:       <working-branch>
Target:       <target-branch>
Draft:        yes | no

Next steps:
  - Request reviewers (at least one reviewer required per project-info.md §12).
  - Watch CI: compile + unit tests + integration tests + JaCoCo gate + frontend lint + frontend tests
    must all be green per project-info.md §12.
  - Address review feedback with new commits — do NOT amend pushed commits.
```
