# Step 2 — Bootstrap `docs/`

**When to run:** after step 1, with `CLAUDE.md` committed.
**Output:** the full `docs/` source-of-truth tree.
**Estimated time:** 30–60 minutes (longest step in the bootstrap).
**Prerequisites:** `CLAUDE.md` and `project-info.md` both at repo root.

---

## What this step produces

`docs/` is the **single source of truth** about what the system is, what it does, and why. It is the *first* place a human or AI looks; it is the *only* place product behaviour and architectural intent are authoritative. `.claude/rules/` (step 3) will cite into here constantly.

Target tree (sections marked **conditional** appear only when `project-info.md` declares the corresponding feature):

```
docs/
├── README.md                            # map of the tree
├── onboarding/README.md                 # get a developer running locally
├── architecture/README.md               # system shape, tech stack, module map, auth flow, config
├── api/README.md                        # endpoint catalog (conditional on backend API)
├── database/                            # conditional on persistence layer
│   ├── README.md                        # ERD + table descriptions
│   └── migrations.md                    # migration policy
├── domain-knowledge/README.md           # product nouns + user journeys + non-goals
├── business-rules/                      # one file per epic
│   ├── README.md                        # enforcement matrix
│   └── <epic>-rules.md                  # one per epic from project-info §5
├── testing/README.md                    # test strategy + coverage targets
├── decisions/                           # ADRs
│   ├── README.md                        # ADR index + procedure
│   ├── template.md                      # ADR template
│   └── 000N-<slug>.md                   # one per row in project-info §10
└── plans/                               # implementation plans (populated later by /make-plan)
    └── .gitkeep
```

> **`docs/` describes WHAT and WHY. `.claude/rules/` (next step) describes HOW.** Never duplicate content between them — always cross-link.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are bootstrapping the `docs/` source-of-truth tree for this repository.

INPUT:
- Read `CLAUDE.md` and `project-info.md` at the repo root. They are the authoritative inputs.
- Read `claude-guidelines/01-project-info-template.md` to know which sections of `project-info.md` map to which docs/ pages.

GOAL:
Produce a complete `docs/` tree. Each file must be useful on its own and cross-link freely with markdown links. Optimize for:
- accuracy — never invent file paths, version numbers, or behaviours not in `project-info.md`
- separation — `docs/` describes WHAT and WHY; HOW lives in `.claude/rules/` (next step)
- specificity — every claim is either a fact from `project-info.md` or marked `(verify)` / `(spec — not yet implemented)`

TARGET TREE (omit conditional rows when project-info.md has them as N/A):

docs/
├── README.md
├── onboarding/README.md
├── architecture/README.md
├── api/README.md                        (skip if §4.1 has no API)
├── database/README.md                   (skip if §4.3 has no DB)
├── database/migrations.md               (skip if no DB or migrations tool listed)
├── domain-knowledge/README.md
├── business-rules/README.md
├── business-rules/<epic-slug>-rules.md  (one per epic in §5)
├── testing/README.md
├── decisions/README.md
├── decisions/template.md
├── decisions/000N-<slug>.md             (one per row in §10; status: Proposed)
└── plans/.gitkeep

WRITING CONVENTIONS:
- Every page starts with a one-sentence "what this page is" line.
- Cross-link with markdown links (relative paths). Use them liberally.
- Use markdown tables for matrices (env vars, NFR ↔ enforcement layer, ADR index, etc.).
- For greenfield projects: every claim that would require code to verify is suffixed `(verify)` or labelled `(spec — not yet implemented)`.
- Avoid first-person ("we", "I"). Use third-person factual prose.
- No emojis.

PER-PAGE CONTENT RULES:

### docs/README.md
- Table of all sub-folders with one-line purpose each.
- Short "How to use this folder" section (3–5 bullets) telling a new contributor where to start for: onboarding, adding a feature, making an architectural change.
- Cross-link to `../CLAUDE.md` and `../.claude/rules/` (note that rules don't exist yet — flag with `(not yet present)`).

### docs/onboarding/README.md
- Goal sentence: "Get the <project> stack running locally in under N minutes" (pick a realistic N).
- Prerequisites table (tool, min version, source).
- Step-by-step bring-up instructions. Use `(spec — not yet implemented)` for any step that requires unwritten code.
- "Something broken?" troubleshooting table (symptom / likely cause / fix). Seed it with 3–5 plausible failures from the stack in §4.

### docs/architecture/README.md
Sections:
1. **What it is** — one paragraph from `§1` + `§3` of project-info.md.
2. **Tech stack at a glance** — ASCII diagram of the major components and their wires. Pull from `§3` and `§4`.
3. **Backend layering** — ASCII tree from `§3.1`. Mark `(spec — not yet implemented)` if no code exists.
4. **Frontend layering** — same, if `§4.2` is not N/A.
5. **How modules connect** — table of (concern → convention) e.g. API base URL, auth scheme, wire format, real-time channel.
6. **Auth flow** — if `§8` has an auth scheme, summarise it; otherwise state "unspecified by spec — see decisions/ once chosen".
7. **Config & profiles** — table from `§14` env-var rows.
8. **Deployment topology** — one paragraph from `§4.6`.

### docs/api/README.md
- Endpoint catalog: one row per endpoint inferred from `§5` (FRs). Columns: method, path, purpose, auth, request body shape, response body shape, error keys.
- For greenfield: every row labelled `(spec — not yet implemented)`.
- "Error response shape" sub-section: state the canonical envelope (e.g. `{ "error_key": "...", "message": "..." }`).

### docs/database/README.md
- ERD as a mermaid `erDiagram` block (or ASCII if mermaid won't render).
- One table description per persistent entity from `§3.1`/`§9`. Columns: column name, type, constraint, purpose.
- Naming conventions section (table_case, PK strategy, timestamp policy).

### docs/database/migrations.md
- Migration tool from `§4.1` (Flyway / Liquibase / Prisma / Alembic / …).
- Naming convention.
- "Forward-only" policy (or explicit exception if the team chose otherwise).
- How seed data is handled.

### docs/domain-knowledge/README.md
1. **What the product is** — one paragraph from `§1` + `§2`.
2. **Core domain concepts** — table of nouns from `§9` (term ↔ meaning).
3. **Typical user journeys** — one numbered list per persona in `§2.1`, walking through what they do.
4. **What this product is NOT** — bullets from `§11` (non-goals).

### docs/business-rules/README.md
- One paragraph explaining what business rules are vs. coding rules (point to `.claude/rules/` once it exists).
- Enforcement matrix: one row per NFR from `§6` (NFR id, rule one-liner, enforcement layer, source link).
- Index of per-epic rule pages.

### docs/business-rules/<epic-slug>-rules.md (one per epic in §5)
- Slug the epic name with kebab-case.
- For each FR in that epic, write a "rule" block:
  - **Rule:** the assertion (one sentence).
  - **Why:** which NFR it serves, or which user need.
  - **Enforced in:** layer/file (use `(verify)` for greenfield).
  - **Failure mode:** what happens when violated (HTTP code, exception, etc.).
  - **Frontend shortcut:** any client-side hint (or "none — server invariant").

### docs/testing/README.md
- Frameworks per layer (from `§4.5`).
- Coverage targets table (from `§4.5` floors).
- What NOT to test (boilerplate exclusion list).
- Test discipline rules (no `@Disabled` / `xit` without ticket, no test order dependence, etc.).

### docs/decisions/README.md
- One paragraph: what ADRs are, when to write one, naming convention `000N-<slug>.md`.
- Procedure: draft as Proposed, link from the PR, mark Accepted/Rejected when merged.
- Index table: number, title, status, date.

### docs/decisions/template.md
- The canonical ADR template. Sections: Status, Date, Deciders, Context, Decision, Options considered (with chosen marker), Consequences (easier/harder/live-with/revisit-if), References.

### docs/decisions/000N-<slug>.md (one per row in `§10`)
- Use the template.
- Status: **Proposed**.
- Context: copy the "Decision needed" + reason from `§10`.
- Options: copy the option list.
- Decision: leave as `_TBD — to be decided._`
- Consequences and References: empty placeholders.

### docs/plans/.gitkeep
- An empty file. Plans are generated by `/make-plan` in step 5 onwards.

OUTPUT:
1. Create the entire tree under `docs/`.
2. Print a tree listing of created files.
3. Print a "Gaps in project-info.md to address before step 3" section if any docs page would benefit from more detail in the input.

DO NOT:
- Read or write any other files outside `docs/`.
- Create or modify `.claude/` — that's step 3.
- Invent endpoints, tables, or rules not derivable from `project-info.md`.
- Use emojis.
- Create a git commit.
```

---

## How to review the output

This is the longest review because everything downstream cites these files. Plan for 10–15 minutes.

| Check | Pass criterion |
|---|---|
| Every epic from `project-info.md §5` has a `docs/business-rules/<epic>-rules.md` | `ls docs/business-rules/*.md` matches your epic count + 1 (README). |
| Every NFR appears in the enforcement matrix in `business-rules/README.md` | Open the file; count rows. |
| Every open ADR in `project-info.md §10` has a `docs/decisions/000N-<slug>.md` file | `ls docs/decisions/0*.md` matches your §10 row count. |
| `docs/architecture/README.md` lists the same modules as `project-info.md §3.1` | Spot-check 2 modules. |
| Cross-links resolve | `grep -r "(../" docs/` and click 3 at random. |
| No invented behaviour (e.g. "uses JWT" when §8 said "unspecified") | Grep for tech terms not in `project-info.md`. |
| The "Gaps" section is short (≤5 entries) | Long gaps = `project-info.md` needs more detail before step 3. |

If any check fails, fix `project-info.md` and rerun in a fresh session.

---

## Commit

```bash
git add docs/
git commit -m "docs: add source-of-truth tree"
```

Then move to [step-3-bootstrap-rules.md](step-3-bootstrap-rules.md).
