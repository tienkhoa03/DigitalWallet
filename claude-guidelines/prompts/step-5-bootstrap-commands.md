# Step 5 — Bootstrap `.claude/commands/`

**When to run:** after step 4 — `.claude/skills/` must exist.
**Output:** `.claude/commands/` directory with 2–3 user-invokable commands.
**Estimated time:** 15–25 minutes.
**Prerequisites:** all previous artifacts (CLAUDE.md, docs/, rules/, skills/) committed.

---

## What this step produces

Commands are **user-invokable workflows** that the user triggers with `/command-name` in a Claude Code session. Each is a single `.md` file in `.claude/commands/`. Commands typically orchestrate skills.

Target baseline commands:

```
.claude/commands/
├── make-plan.md          # codebase-aware implementation plan generator
└── implement-plan.md     # executes a plan file by delegating to agents
```

These two compose into the team's core "plan first, implement second" workflow. Add more later as the team identifies recurring multi-step rituals.

---

## Copy-paste prompt

> Paste verbatim into a fresh Claude Code session opened at the project root.

```
You are bootstrapping `.claude/commands/` for this repository.

INPUT:
- Read `CLAUDE.md`, the `docs/` tree, `.claude/rules/`, and `.claude/skills/`.
- Read `claude-guidelines/reference/what-each-artifact-is-for.md` if present.

GOAL:
Produce two slash commands that compose the workflow:
1. `/make-plan <task>` — creates a structured implementation plan as a markdown file under `docs/plans/`.
2. `/implement-plan <path-to-plan>` — executes a plan by dispatching to specialist agents (created in step 6).

Both commands MUST:
- invoke the relevant skills from step 4 FIRST (skills before research)
- read `.claude/rules/` so the plan reflects current standards
- use `AskUserQuestion` when input is genuinely ambiguous
- NOT create git commits

TARGET FILES:

.claude/commands/
├── make-plan.md
└── implement-plan.md

WRITING CONVENTIONS:
- Each command starts with a `# <Command Title>` heading and a one-paragraph intro.
- The intro states clearly: planning-only OR implementing OR analytical.
- Numbered "## Step N: ..." sections drive the procedure.
- Cite skills as `Skill("<skill-name>")` and agents as `@<agent-name>` so they're greppable.
- Reference rule files with relative markdown links.
- No emojis.

PER-COMMAND CONTENT:

### make-plan.md
- Title: "# Create Implementation Plan"
- Intro: "PLANNING ONLY — Do NOT implement code. After the plan is approved, invoke `/implement-plan`."
- Step 1: Parse input — accept plain text, ticket #, URL; use `AskUserQuestion` only when truly ambiguous.
- Step 2: Invoke skills FIRST. Table of (trigger → skill → purpose). Detection rules for backend vs frontend vs both.
- Step 3: Read project guidelines — `CLAUDE.md`, the relevant rule files, the relevant `docs/` pages.
- Step 4: Research phase — use `Agent` with `subagent_type=Explore` for broad questions; `Glob`/`Grep`/`Read` for targeted lookups.
- Step 5: Slugify the task title (lowercase, hyphenate, max 60 chars).
- Step 6: Create `docs/plans/` if it doesn't exist.
- Step 7: Write the plan document at `docs/plans/implementation-plan-<slug>.md`. Required sections, in order:
  1. Header (title, date, ticket, story points / T-shirt size — NEVER wall-clock time, milestone, assignees, affected modules, suggested branch name).
  2. Context / Problem Statement (2–4 paragraphs citing FR/NFR/ADR).
  3. Scope (In Scope / Out of Scope bullets).
  4. Open Questions (MANDATORY) — table with status legend `❓ Unanswered | ✅ Answered | ⏳ Deferred`. Plan cannot be approved with `❓ Unanswered` remaining.
  5. Technical Approach / Architecture Decisions — reference specific rule sections and files; include a Mermaid diagram if the change crosses major boundaries.
  6. Applicable Rules, Skills & Agents — table.
  7. File Structure — ASCII tree of directories that will be touched.
  8. Files to Modify / Create — table (module, path, action, layer).
  9. Progress Tracker — phases as a checklist, each tagged with the executing agent and skill.
  10. Acceptance Criteria — checkbox format, each objectively verifiable.
  11. Security Considerations (MANDATORY) — map every change to the relevant section of `security.md`.
  12. Testing Strategy — unit, integration, frontend specs, coverage floor.
  13. Reference Files — bullet list of existing files the implementer should read first.
  14. Risks & Dependencies.
- Step 8: Validate the plan (does it cover every aspect, are paths real, are phases in logical order, are open questions captured).
- Step 9: Present and iterate — print the file path, suggest the first skill, use AskUserQuestion to collect feedback.

### implement-plan.md
- Title: "# Implement Plan"
- Intro: "Execute an implementation plan from a `.md` file, delegating work to specialized developer agents in parallel where possible."
- Step 1: Validate and parse the plan file (error if missing/non-.md/has unanswered open questions).
- Step 2: Categorize tasks by module — backend → `@backend-developer`, frontend → `@frontend-developer`, cross-module → orchestrator.
- Step 3: Explore shared context — read the plan fully, every Reference File, every API contract page the plan touches; note cross-module dependencies (shared DTO shapes, enum values, error keys).
- Step 4: Dispatch to developer agents. CRITICAL: when multiple agents are needed, spawn them in a SINGLE message with multiple `Agent` tool calls so they run concurrently.
- Each agent prompt MUST include: full plan context, that agent's specific subset of tasks, cross-module dependencies it must honor (literal shapes — do not paraphrase), relevant rules and docs, verification command, and "DO NOT create git commits" instruction.
- Step 5: Handle cross-module tasks (docs/ updates, root config, integration wiring) yourself.
- Step 6: Verify the combined result — `Skill("backend-verify")` if backend touched, `Skill("frontend-verify")` if frontend touched, `Skill("code-review")` always. Route failures back to the responsible agent with the specific error excerpt.
- Step 7: Report — print a "## Implementation Report" with sections: Plan, Title, Execution mode, Agent Results (per agent: phases completed, files touched, verification result), Cross-Module Tasks, Verification Results table, Acceptance Criteria, Issues Encountered, Next Steps.
- Important rules at the bottom: DO NOT create commits; DO spawn agents in parallel; DO give each agent the full plan; DO include cross-module contracts in both prompts; DO invoke code-review at the end; DO implement fully without pausing per phase (the user already approved); DO NOT skip a verify step; DO NOT modify the plan file during execution.

PROCESS:
1. Write both files.
2. Cross-check: every cited skill in the commands exists in `.claude/skills/`; every cited agent will exist after step 6 (note this with a "(will be created in step 6)" comment in the file if helpful — but the agent name itself must be the final name).

OUTPUT:
1. Print created file paths.
2. Print a "Skill citation check" — list every `Skill("...")` reference in each command and confirm it exists.
3. Print an "Agent reference check" — list every `@<agent>` reference; flag as `(pending step 6)` since agents don't exist yet.

DO NOT:
- Read or write other directories.
- Create agents — that's step 6.
- Invent skills that weren't created in step 4.
- Use emojis.
- Create a git commit.
```

---

## How to review the output

| Check | Pass criterion |
|---|---|
| `make-plan.md` produces planning-only output (no code) | grep for "implement" — should only appear in the file's "Important Rules" warning. |
| Both commands invoke skills before doing work | Open each; "Step 2" or earlier should call `Skill(...)`. |
| Skill citations resolve to existing files | Read the report's "Skill citation check". |
| Agent citations are noted as `(pending step 6)` or unsuffixed but matching the names you intend to create | Read the report's "Agent reference check". |
| Neither command creates commits | grep for `git commit` — should only appear in disclaimers/instructions. |
| `make-plan.md` has Open Questions and Security Considerations as MANDATORY sections | Open it; verify. |

If any check fails, rerun in a fresh session.

---

## Commit

```bash
git add .claude/commands/
git commit -m "claude: add slash commands"
```

Then move to [step-6-bootstrap-agents.md](step-6-bootstrap-agents.md).
