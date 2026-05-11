# Step 7 — Validation Pass

**When to run:** after step 6 — all artifacts created.
**Output:** either a "no fixes needed" report OR a small set of patches that fix cross-cutting issues.
**Estimated time:** 15–30 minutes.
**Prerequisites:** every previous commit in place.

---

## What this step does

A final integrity pass over the whole `claude-ready` baseline. It checks:

1. **Cross-link integrity** — every markdown link inside `CLAUDE.md`, `docs/`, and `.claude/` resolves to a file that exists.
2. **Terminology consistency** — the same noun used the same way across all artifacts (e.g. you call them "wallets" everywhere, not "accounts" in some files and "wallets" in others).
3. **Citation coverage** — every rule section cited by a skill or agent actually exists; every skill cited by a command exists; every agent cited by a command exists.
4. **NFR ↔ enforcement coverage** — every NFR from `project-info.md §6` is enforced by at least one rule + at least one test guidance entry.
5. **Skill ↔ trigger coverage** — every skill has a non-overlapping trigger description so routing is unambiguous.
6. **Gaps surfaced earlier** — anything flagged in the "Gaps" sections of previous steps is now resolved.

The validation produces either:

- **PASS** — print a short summary, no fixes needed.
- **FIXES** — print a patch list and apply low-risk fixes (broken link targets, typos, missing cross-references) directly.
- **ESCALATE** — print issues that require human judgment (e.g. "the security rule says JWT, but project-info.md says auth is unspecified") and stop without patching.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are running a final validation pass on the Claude-ready baseline of this repository.

INPUT:
- The entire repo: `CLAUDE.md`, `project-info.md` (if still present), `docs/`, `.claude/rules/`, `.claude/skills/`, `.claude/commands/`, `.claude/agents/`.
- Read `claude-guidelines/03-initialization-workflow.md` for context on what the bootstrap was meant to produce.

GOAL:
Verify the baseline is internally consistent. Apply low-risk fixes directly. Escalate anything that requires human judgment.

CHECKS:

### 1. Cross-link integrity
- Walk every `*.md` file in `CLAUDE.md`, `docs/`, `.claude/rules/`, `.claude/skills/`, `.claude/commands/`, `.claude/agents/`.
- Extract every markdown link `[text](path)` where `path` is relative.
- For each link, resolve from the file's directory and check the target exists.
- Report broken links per (source-file, line, target).

### 2. Terminology consistency
- Pull the glossary from `docs/domain-knowledge/README.md`.
- For each term, grep the entire repo for usage. Flag inconsistent spellings or synonyms (e.g. "wallet" vs "account" used interchangeably outside of the glossary's defined meaning).

### 3. Citation coverage
- For each `Skill("<name>")` reference in `.claude/commands/` and `.claude/agents/`: confirm `.claude/skills/<name>/SKILL.md` exists.
- For each `@<agent>` reference in `.claude/commands/`: confirm `.claude/agents/<agent>.md` exists.
- For each `.claude/rules/<file>.md §N` reference in skills, agents, commands, and docs: confirm the file exists AND the §N anchor exists (look for the `## N.` or `### N.x` heading).
- Report broken references per (source-file, line, target).

### 4. NFR ↔ enforcement coverage
- Pull every NFR from `docs/business-rules/README.md` (and the per-epic rule pages).
- For each NFR, confirm:
  a. At least one rule file (`.claude/rules/*.md`) cites it or enforces it.
  b. `.claude/rules/testing.md` includes test guidance for it (boundary, replay, ownership, etc.).
  c. `.claude/rules/security.md §12` (code-review checklist) covers it OR is N/A (e.g. for non-security NFRs).
- Report any NFR with < 1 rule and < 1 test guidance entry.

### 5. Skill trigger uniqueness
- Read the `description` frontmatter of every SKILL.md.
- Cluster trigger phrases. Flag any pair of skills that share 2+ trigger phrases (likely routing conflict).
- Suggest a tightened description for each conflicted skill.

### 6. Greenfield markers (only if no source code exists yet)
- Confirm every claim about file paths, version numbers, or behaviour is either backed by `project-info.md` or marked `(verify)` / `(spec — not yet implemented)`.
- Flag any unverified claims (e.g. "the `TransferService.transfer()` method uses pessimistic locking" when no Java file exists yet — should be `(spec)`).

### 7. Earlier-step gap closure
- Re-read the "Gaps" output from earlier bootstrap steps (if you can find them via git log or memory). For each item that was flagged as a gap in step N, confirm it's been addressed in steps N+1..6. List the still-open ones.

PATCH POLICY:
- **Apply directly** when the fix is: a broken link target, an obvious typo, a missing cross-reference that the surrounding text clearly intended to make, or normalising the same term used two different ways within a single file.
- **Print and escalate** when the fix requires interpretation: a missing NFR enforcement, a contradicting fact between `project-info.md` and another file, a missing skill/agent for a major use case, or anything that touches numbered rule sections (renumbering breaks every citation).

OUTPUT:
Print a single structured report:

```
## Validation Report

**Repo state:** <greenfield | partial code | complete code>
**Verdict:** PASS | FIXES_APPLIED | ESCALATE

### 1. Cross-link integrity
- <n> links scanned, <m> broken
- <list — or "all resolve">

### 2. Terminology consistency
- <list of inconsistent uses>

### 3. Citation coverage
- Skills cited: <n> | Missing: <list>
- Agents cited: <n> | Missing: <list>
- Rule sections cited: <n> | Missing: <list>

### 4. NFR ↔ enforcement coverage
- <list of NFRs without rule coverage>
- <list of NFRs without test guidance>

### 5. Skill trigger uniqueness
- <list of conflicting trigger pairs and recommended tightenings>

### 6. Greenfield markers
- <list of unverified claims missing `(verify)` markers>

### 7. Earlier-step gap closure
- <list of still-open gaps>

### Patches Applied
- <list of low-risk fixes applied directly>

### Escalations (Human Action Required)
- <list of issues requiring human judgment>
```

DO NOT:
- Renumber rule sections (breaks every downstream citation — escalate instead).
- Delete files.
- Rewrite skill/agent prompts wholesale.
- Use emojis.
- Create a git commit.
```

---

## How to act on the output

- **PASS** — you're done. Tag the commit: `git tag -a v0.0.0-claude-bootstrap -m "Claude-ready baseline"`.
- **FIXES_APPLIED** — review the patch list. If you're happy, commit them with `git add -A && git commit -m "claude: validation fixes"`. If anything looks wrong, revert and rerun.
- **ESCALATE** — read every escalation. For each:
  - If it's a broken design (e.g. NFR not enforced anywhere), update `project-info.md` and rerun the affected bootstrap step in a fresh session.
  - If it's a contradiction, decide which side is right, fix it manually, and re-validate.
  - If it's a missing skill/agent, add it manually using existing files as templates.

---

## When to rerun the whole bootstrap

Almost never. The bootstrap is for greenfield. After the first commit, you maintain by:

- Editing rule files when conventions change.
- Adding new skills under `.claude/skills/<slug>/SKILL.md`.
- Writing new ADRs under `docs/decisions/`.
- Updating `CLAUDE.md` when the architecture shifts.

Full rerun is only justified for a foundational rewrite (swap framework / language / paradigm).

---

## Commit (if patches were applied)

```bash
git add -A
git commit -m "claude: validation fixes"
git tag -a v0.0.0-claude-bootstrap -m "Claude-ready baseline"
```

You're done. Open a fresh Claude session and try `/make-plan <a small feature>` to verify the workflow end-to-end.
