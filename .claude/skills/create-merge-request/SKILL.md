---
name: create-merge-request
description: Use when the user asks to "create a merge request", "open an MR", "create a PR", "push and create MR", "raise a merge request", or any equivalent. Pushes the current work to a feature branch and opens a GitHub pull request via `gh`. Every branching decision goes through AskUserQuestion — never assumed.
---

# Create Merge Request

Open a GitHub pull request from the current work. Conservative by default: never destructive without explicit confirmation, never on `main`, never silent about uncommitted changes.

> Platform: GitHub via the `gh` CLI. The CLI must be installed and authenticated (`gh auth status`). If not, stop and tell the user.

## Step 1 — Pre-flight

Run in parallel:

```bash
git status --short
git rev-parse --abbrev-ref HEAD
git config --get remote.origin.url
gh auth status
```

Decide based on the output:

- **`gh` not authenticated** → stop, print: `gh CLI is not authenticated. Run \`gh auth login\` and retry.`
- **No `origin` remote** → stop, print: `No origin remote configured. Add a remote and retry.`
- **`origin` is not GitHub** → stop, print: `Remote is not GitHub; this skill targets GitHub via gh. Use the platform's native CLI instead.`

## Step 2 — Handle uncommitted changes

If `git status --short` shows any uncommitted changes, ask the user via `AskUserQuestion`:

| Header | Question | Options |
|---|---|---|
| Uncommitted | There are uncommitted changes — how should I handle them? | (a) Commit all to a new commit on this branch, (b) Commit only specific files (I'll list them), (c) Stash and proceed, (d) Cancel |

Do NOT silently commit, stash, or discard. Wait for the answer.

If (a) or (b): draft a commit message before committing (Conventional Commits short form, ≤ 72 chars subject, optional body). Use a HEREDOC. Run hooks normally — do not pass `--no-verify`.

## Step 3 — Resolve the working branch

If the current branch is `main` (or whatever the default branch is), ask via `AskUserQuestion`:

| Header | Question | Options |
|---|---|---|
| New branch | You're on `main`. Create a feature branch? | (a) Yes — `feature/<slug>` (suggest a slug from the staged diff), (b) Yes — let me name it (free-text), (c) Cancel |

Never push to `main` directly. Never force-push to `main`.

If on a feature branch already, confirm via `AskUserQuestion`:

| Header | Question | Options |
|---|---|---|
| Confirm branch | Open the PR from `<current-branch>` against `main`? | (a) Yes, (b) Choose a different target branch (free-text) |

## Step 4 — Identify the target branch

Default target: the repo's default branch (`gh repo view --json defaultBranchRef -q .defaultBranchRef.name`). If the user named a target in step 3, use that instead.

Compute the merge-base with the target:

```bash
git fetch origin
git merge-base HEAD origin/<target>
```

If the merge-base equals `HEAD`, there is nothing to merge — stop and tell the user.

## Step 5 — Push

```bash
git push -u origin <branch>
```

If the remote branch already exists and has diverged, **do not** force-push. Stop, tell the user, and ask via `AskUserQuestion` whether to (a) rebase onto the remote tip then push, (b) force-with-lease, or (c) cancel. Force-with-lease only on explicit confirmation.

## Step 6 — Draft title and body

- **Title**: ≤ 70 chars, imperative, no trailing period. Pull from the most recent commit's subject if it summarizes the diff; otherwise synthesize from the changed files.
- **Body**: use the template below, written via HEREDOC.

```markdown
## Summary

<1-3 bullets describing what changed and why>

## Changes

<bulleted list of the substantive changes; group by module if more than one>

## Related issues

<linked issues, e.g., "Closes #123" or "—" if none>

## Test plan

- [ ] <how to verify the change locally>
- [ ] <regression to watch for>
- [ ] <new test that must pass>
```

Pull the substantive changes from `git diff <merge-base>...HEAD --stat` and the per-file diffs.

## Step 7 — Create the PR

```bash
gh pr create --base <target> --head <branch> --title "<title>" --body "$(cat <<'EOF'
<body>
EOF
)"
```

If the branch is still in progress, ask via `AskUserQuestion` whether to add `--draft`. Default: not draft.

## Step 8 — Report

Print:

- Branch pushed: `<branch>` → `origin/<branch>`.
- Target: `<target>`.
- PR URL (from `gh pr create` stdout).
- Title used.
- Anything still uncommitted (should be empty after step 2).
- Suggest invoking `code-review` against the PR diff if not already done.

## Discipline

- Every branching decision uses `AskUserQuestion`. Do not infer.
- Never `git push --force` to `main` (or the default branch). Warn loudly even if the user asks.
- Never `--no-verify` to bypass hooks. If a hook fails, surface the error to the user.
- Never `git reset --hard` without explicit confirmation.
