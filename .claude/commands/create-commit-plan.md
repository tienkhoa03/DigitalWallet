# Create Commit Plan

Analyze the current working tree and propose a sequence of **atomic, dependency-ordered commits** with Conventional Commits messages, following the workflow in [project-info.md §12](../../project-info.md#12-development-workflow). The plan is presented for approval **before** any commit is created — committing happens only after an explicit confirmation step (Step 6).

This is a planning + (optionally) execution command for *local history hygiene*. It is distinct from:

- [`/make-plan`](make-plan.md) — plans **future implementation work** into a `docs/plans/` file consumed by `/implement-plan`. `/create-commit-plan` plans how to **commit work that already exists** in the working tree.
- [`Skill("create-merge-request")`](../skills/create-merge-request/SKILL.md) — pushes and opens the **PR**. The natural chain is: `/create-commit-plan` (structure the commits) → `Skill("create-merge-request")` (push + open PR).

## When NOT to invoke

- There is nothing uncommitted (`git status` clean) — there is nothing to plan. Tell the user.
- The user wants a single quick commit of everything — a one-line `git commit` is simpler; this command earns its keep when the diff spans multiple logical changes.
- The user wants to open a PR — that is [`Skill("create-merge-request")`](../skills/create-merge-request/SKILL.md). (It will itself offer to commit uncommitted changes; use this command first when the changes deserve to be split.)
- The user wants to review code quality first — run [`/review-code`](review-code.md) before planning commits.

## Step 1: Pre-flight

Run in parallel and read the full picture before grouping anything:

- `git status --short` — staged, unstaged, and untracked files.
- `git rev-parse --abbrev-ref HEAD` — current branch.
- `git diff --stat` and `git diff` — unstaged content.
- `git diff --cached --stat` and `git diff --cached` — already-staged content.
- `git log --oneline -10` — recent history, to match the project's commit-message style.

Read enough of the actual diff hunks to understand *what changed and why* — the plan's quality depends on understanding the change, not just file names. For untracked files, read them.

## Step 2: Screen for secrets and noise

Before proposing any commit:

- **Flag and EXCLUDE** any file that looks like a secret or local artifact — `*.env`, `*.pem`, `*.key`, keystores, `credentials*.json`, `*.local.*` (per [security.md §1](../rules/security.md#1-secrets-and-configuration), [security.md §10](../rules/security.md#10-secret-scanning)). Never stage these. Call them out in the report as "excluded — do not commit".
- **Flag** build output, `node_modules/`, `target/`, coverage reports, and editor files. These usually belong in `.gitignore`, not a commit — surface them, do not auto-stage.
- The `gitleaks` pre-commit hook ([project-info.md §12](../../project-info.md#12-development-workflow)) is the backstop, but the plan should not rely on it: do not propose committing anything that trips it.

## Step 3: Group changes into atomic commits

Each commit MUST be **one logical change** that builds and tells one story. Grouping heuristics, in priority order:

1. **By concern / change type** — a refactor, a feature, a bugfix, a docs edit, and a chore are separate commits even if touched in one session. This maps directly to the Conventional Commits prefix (Step 4).
2. **By feature module** — keep a vertical slice together. A wallet-deposit change groups its migration + entity + repository + service + resource + tests into *one* `feat` commit (the slice builds atomically); do **not** split tests into their own commit unless they stand alone (e.g. backfilling coverage for existing code → a `test:` commit).
3. **Contract before consumer** — when a change spans backend and frontend, the backend contract (endpoint, DTO, `errorKey`) commits **before** the frontend that consumes it, so each commit is independently coherent.
4. **Cross-cutting first** — `shared/` infrastructure, a Flyway migration, or an `errorKey` addition that later commits depend on goes earliest.
5. **Docs / config last** — `docs/`, `.claude/`, ADRs, and root config land after the code they describe, unless an ADR must precede the code it authorizes (then it leads).

A file that legitimately serves two commits (e.g. a shared constants file touched by two features) is staged with whichever commit it primarily belongs to — note the coupling rather than splitting a single file across commits with `git add -p` (interactive staging is not available in this environment).

## Step 4: Draft a Conventional Commits message per commit

Per [project-info.md §12](../../project-info.md#12-development-workflow), the allowed prefixes are exactly: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`. For each planned commit:

- **Subject:** `<type>(<scope>): <imperative summary>`, ≤ 70 chars. Scope is the feature module (`wallet`, `fraud`, `pfm`, `advisor`, `dashboard`, `account`, `shared`) or a frontend feature folder. Examples: `feat(wallet): add deposit endpoint with idempotency`, `test(fraud): cover velocity boundary at threshold+1`, `docs: add commit-plan command`.
- **Body (when the why is non-obvious):** 1–3 bullets tying back to the FR / NFR / ADR or rule that motivated it (e.g. "implements FR1.2 — see docs/business-rules/core-wallet-management-rules.md").
- Tie NFR-touching changes (NFR1 concurrency, NFR2 outbox, NFR3 idempotency, NFR6 CQRS, NFR7 event-time, NFR8 LLM isolation) to the invariant in the body so history stays auditable.

## Step 5: Present the plan

Render the plan as an ordered table — do not commit yet:

```
commit-plan
───────────
Branch:   <current-branch>   (target after branching, if on main — see Step 7)
Excluded: <secret/noise files that will NOT be committed, with reason>

 #  Type        Files                                  Message
 1  feat(shared) backend/.../V2__add_x.sql, X.java     feat(shared): add outbox appender
 2  feat(wallet)  backend/wallet/**                     feat(wallet): add deposit endpoint
 3  test(wallet)  backend/wallet/**/*Test.java          test(wallet): cover deposit replay
 4  docs          docs/api/README.md                    docs(api): document POST deposit
```

For each commit, also show the full drafted message (subject + body) so the user can approve the wording, not just the grouping.

## Step 6: Confirm execution

Use a single `AskUserQuestion` to decide what happens next. Never commit without this confirmation — the user invoking this command asked for a *plan*; committing is a separate, explicit step (harness rule: commit only when the user asks).

- **Execute the plan** *(recommended)* — create the commits in order as drafted.
- **Plan only** — print the plan (and, optionally, the exact `git add` / `git commit` commands) and stop; the user commits manually.
- **Revise** — adjust grouping, ordering, or messages, then re-present (return to Step 3/4).

## Step 7: Execute (only if approved)

If — and only if — the user chose "Execute the plan":

1. **Branch guard.** If the current branch is the default (`main`), refuse to commit onto it (trunk-based model, [project-info.md §12](../../project-info.md#12-development-workflow)). Ask for a feature branch name (`feat/<slug>`, `fix/<slug>`, …) and run `git checkout -b <name>` before the first commit.
2. **Per commit, in order:**
   - Stage exactly that commit's file list explicitly — `git add <file> <file> …`. Never `git add -A` / `git add .` (it would sweep in excluded and out-of-scope files). Interactive staging (`git add -i`, `git add -p`) is not available here — stage whole files only.
   - Commit with a HEREDOC message, ending every message with the required trailer:

     ```
     git commit -m "$(cat <<'EOF'
     <type>(<scope>): <subject>

     <body bullets>

     Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
     EOF
     )"
     ```
   - **Pre-commit hooks run normally** — gitleaks, Spotless (backend) / Prettier (frontend), and the fast lint pass ([project-info.md §12](../../project-info.md#12-development-workflow)). MUST NOT pass `--no-verify`. If a hook reformats files or fails, fix the underlying issue and re-stage; if the formatter modified files, amend them into the **same** commit (it has not been pushed yet). Do not silence a failing hook.
3. Stop on the first commit that fails for a non-formatting reason; report which commits succeeded so the user can resume.

## Step 8: Report and next steps

```
commit-plan report
──────────────────
Branch:     <branch>           (created from main? yes/no)
Commits:    <N created>
  <sha>  <type>(<scope>): <subject>
  ...
Excluded:   <files intentionally not committed>
Remaining:  <any uncommitted changes left on purpose>

Next steps:
  - Review history:  git log --oneline
  - Open the PR:      Skill("create-merge-request")
```

## Important rules

- **DO NOT** commit before Step 6 confirmation — the plan is the default deliverable; committing is opt-in.
- **DO NOT** stage with `git add -A` / `git add .` — stage explicit per-commit file lists so excluded and out-of-scope files stay out.
- **DO NOT** commit secrets or local artifacts (Step 2); **DO NOT** pass `--no-verify` to bypass a hook ([security.md §10](../rules/security.md#10-secret-scanning)).
- **DO NOT** commit onto `main` — branch first (Step 7).
- **DO NOT** rewrite or amend already-pushed history; this command only builds new local commits.
- **DO** end every commit message with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer.
- **DO** keep each commit atomic and Conventional-Commits-compliant; **DO** chain into [`Skill("create-merge-request")`](../skills/create-merge-request/SKILL.md) once history looks right.
