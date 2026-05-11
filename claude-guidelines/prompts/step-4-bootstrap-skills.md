# Step 4 — Bootstrap `.claude/skills/`

**When to run:** after step 3 — `.claude/rules/` must exist.
**Output:** `.claude/skills/` directory with 5–7 skill folders.
**Estimated time:** 20–40 minutes.
**Prerequisites:** `CLAUDE.md`, `docs/`, `.claude/rules/` all committed.

---

## What this step produces

Skills are **repeatable, parameterised procedures** Claude can invoke during a task. Each lives in its own folder as `.claude/skills/<skill-name>/SKILL.md`. The `SKILL.md` contains:

- A `name` and `description` frontmatter — used by Claude to decide when to invoke.
- A series of numbered steps (input gathering, placement confirmation, generation, self-check, report).
- Citations into `.claude/rules/` for every conventional choice.

Target baseline skills (conditional rows skip when `project-info.md` says N/A):

```
.claude/skills/
├── backend-create-rest-api/SKILL.md         # if §4.1 declares an API style
├── backend-create-unit-test/SKILL.md        # if §4.5 has a backend unit-test framework
├── backend-verify/SKILL.md                  # always for backend projects
├── frontend-implement-ui-component/SKILL.md # if §4.2 not N/A
├── frontend-verify/SKILL.md                 # if §4.2 not N/A
├── code-review/SKILL.md                     # always
└── create-merge-request/SKILL.md            # if §12 names a PR/MR platform
```

Add more later as patterns emerge (e.g. `backend-create-kafka-consumer`, `frontend-create-spec-only`, etc.). The bootstrap establishes the baseline; growth is incremental.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are bootstrapping `.claude/skills/` for this repository.

INPUT:
- Read `CLAUDE.md`, the `docs/` tree, and the `.claude/rules/` tree.
- Read `claude-guidelines/reference/what-each-artifact-is-for.md` if present, for the skill ↔ command ↔ agent separation.

GOAL:
Produce a baseline set of skills that:
- automate the most common multi-step procedures in this stack
- invoke the rules from step 3 — cite, do not paraphrase
- have a deterministic structure (Step 1 — gather inputs → Step 2 — confirm placement → Step 3 — write files → Step 4 — self-check → Step 5 — report)

TARGET SKILLS (omit conditional ones based on `project-info.md`):

.claude/skills/
├── backend-create-rest-api/SKILL.md           (§4.1 has an API)
├── backend-create-unit-test/SKILL.md          (§4.5 backend test framework declared)
├── backend-verify/SKILL.md                    (backend exists)
├── frontend-implement-ui-component/SKILL.md   (§4.2 not N/A)
├── frontend-verify/SKILL.md                   (§4.2 not N/A)
├── code-review/SKILL.md                       (always)
└── create-merge-request/SKILL.md              (§12 names a PR platform)

WRITING CONVENTIONS:
- Each SKILL.md starts with YAML frontmatter:
  ---
  name: <skill-slug>
  description: <one-paragraph trigger description — include 5–8 example user phrasings that should invoke it>
  ---
- After frontmatter: a `# <Skill Title>` heading, then a short intro paragraph.
- A "When NOT to invoke" section listing 2–4 cases the user should use a different skill or just edit files directly.
- Numbered steps with imperative voice ("Read the target class", "Generate the test file").
- Every conventional choice cites a rule file by section, NEVER restates it. Example: "AssertJ for assertions ([testing.md §2.5](../../rules/testing.md))".
- For input gathering: use ONE `AskUserQuestion` call with 2–4 questions; ask only what isn't already in the user's prompt.
- No emojis.

PER-SKILL CONTENT:

### backend-create-rest-api/SKILL.md
- Scaffolds a complete vertical slice: migration → entity → repository → DTOs → service interface + impl → resource → test.
- Inputs: Resource name (singular CamelCase), Operations (multi-select: Create/Read/List/Update/Delete), Auth policy per operation, Constraints (Idempotency-Key required, pessimistic lock, neither), entity fields.
- Step 2: print planned file list and chosen feature module, confirm.
- Step 3: bottom-up file order, each citing the appropriate rule section.
- Step 4: self-check list aligned with the relevant `backend_coding.md` and `security.md` sections.
- Step 5: report with rules where the request was adjusted; suggest `backend-verify` next.
- If the backend module isn't scaffolded yet, stop and tell the user — do NOT bootstrap build files.

### backend-create-unit-test/SKILL.md
- Generates a `<Class>Test.java` (or equivalent) for a target class.
- Step 1: read target class — identify public methods, injected collaborators, Clock usage, declared exceptions.
- Step 2: decide what to mock — apply the matrix from `testing.md`.
- Step 3: enumerate scenarios — happy path, each domain exception, boundary, concurrency/lock, idempotency.
- Step 4: generate the test file in the mirrored test package.
- Conventions: method naming `<methodUnderTest>_<scenario>_<expectedOutcome>`, assertions library from `testing.md`.

### backend-verify/SKILL.md
- Step 1: detect-and-skip preflight — if build files aren't present, emit `PASS (skipped) — backend module not yet scaffolded` and stop. CRITICAL — do not run build tools against a missing project.
- Step 2: sequential pipeline (compile → unit tests → integration/coverage). Stop on first failure.
- Step 3: coverage reading — read JaCoCo CSV (or equivalent) and check service-layer floor from `testing.md`.
- Step 4: structured report with PASS/FAIL verdict per step.
- Discipline: never skip failing tests, never lower coverage floor without an ADR.

### frontend-implement-ui-component/SKILL.md
- Scaffolds a new component (page / presentational / form) with state, API wiring, validation, route protection, and spec.
- Inputs: component type, API endpoints, auth requirement, new global state needed, form fields.
- Step 2: confirm placement; halt if frontend module isn't scaffolded.
- Step 3: generate files using rules from `frontend_coding.md`.
- Required spec tests: render, form happy-path, form invalid, XSS regression (per `testing.md`).
- Cite `docs/business-rules/` for every backend-aligned validator.

### frontend-verify/SKILL.md
- Step 1: detect-and-skip preflight on missing `package.json` / framework config.
- Detect package manager from lockfile.
- Step 2: pipeline (lint → build → test). Stop on first failure.
- CRITICAL: pass non-interactive test runner flags (e.g. `--watch=false`, `--browsers=ChromeHeadless` for Karma; `--watch=false --ci` for Jest) so the runner doesn't hang in CI/non-TTY shells.
- Step 3: structured report.

### code-review/SKILL.md
- Step 1: establish what to review — `git status --short`, current branch, merge-base with default branch, diff. If user named a file, narrow scope.
- Step 2: detect modules from changed-file paths; load only the relevant rule files.
- Step 3: walk every rule against the diff; map MUST/MUST NOT/Never to severity `block`; map Prefer/Avoid to `warn`.
- Apply `security.md §12` checklist line-by-line at the end.
- Step 4: structured report with PASS/FAIL verdict.
- Edge cases: `<!-- not-yet-adopted -->` rules downgrade to `info`; auto-generated files skip rule application; docs-only changes switch to content review.
- Discipline: don't invent rules, cite section numbers, no stylistic findings outside rules.

### create-merge-request/SKILL.md (conditional on §12 PR platform)
- Platform-specific (GitHub via `gh`, GitLab via `glab`, etc.).
- Step 1: pre-flight (`git status`, branch, remote URL, auth status).
- Step 2: handle uncommitted changes — ASK via AskUserQuestion (commit all / commit specific / stash / cancel). Never silently commit.
- Step 3: resolve working branch — never push to default branch directly.
- Step 4: identify target branch.
- Step 5: push with `-u` flag if needed; never force-push without explicit confirmation.
- Step 6: draft title (≤70 chars, imperative) and body (Summary, Changes, Test Plan, optional Risk).
- Step 7: open PR via platform CLI.
- Step 8: print PR URL and next steps.

PROCESS:
1. Create the `.claude/skills/<skill-name>/` directory for each skill.
2. Write a SKILL.md inside each.
3. Cross-check that every cited rule section actually exists in `.claude/rules/`.

OUTPUT:
1. Print a tree listing of created skill folders.
2. Print a "Trigger phrase coverage" check — list each skill and the user phrasings in its description, flag any near-duplicates between skills that could confuse routing.
3. Print a "Rule citation check" — scan each SKILL.md and report any cited rule section that doesn't exist.

DO NOT:
- Read or write any other files outside `.claude/skills/`.
- Create commands or agents — those are later steps.
- Paraphrase rule content — only cite.
- Invent skills not on the target list.
- Use emojis.
- Create a git commit.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| Each skill folder contains exactly `SKILL.md` | `find .claude/skills -type f` — every file ends in `SKILL.md`. |
| Each SKILL.md frontmatter has `name` and `description` | `head -5 .claude/skills/*/SKILL.md` |
| Trigger phrases don't overlap between skills | Read the report's "Trigger phrase coverage" section. |
| Rule citations resolve | Read the report's "Rule citation check" section — should be empty. |
| `code-review` skill loads only the rules for changed files | Open it; verify the file-pattern → rules table is present. |
| `*-verify` skills have the detect-and-skip preflight | Open both; verify the first step. |
| `create-merge-request` never force-pushes without confirmation | Open it; grep for `force` — must be gated by AskUserQuestion. |

If any check fails, rerun in a fresh session.

---

## Commit

```bash
git add .claude/skills/
git commit -m "claude: add skills"
```

Then move to [step-5-bootstrap-commands.md](step-5-bootstrap-commands.md).
